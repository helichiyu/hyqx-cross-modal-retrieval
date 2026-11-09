package com.hsmty.cmr.domain.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.auth.AuthContext;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.search.dto.SearchExecuteDTO;
import com.hsmty.cmr.domain.search.dto.UnderstandDTO;
import com.hsmty.cmr.domain.search.entity.CmrClickEvent;
import com.hsmty.cmr.domain.search.entity.CmrResultItem;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.ClickEventMapper;
import com.hsmty.cmr.domain.search.mapper.ResultItemMapper;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.search.vo.HistoryDetailVO;
import com.hsmty.cmr.domain.search.vo.HistoryVO;
import com.hsmty.cmr.domain.search.vo.ResultItemVO;
import com.hsmty.cmr.domain.search.vo.SearchResultVO;
import com.hsmty.cmr.domain.search.vo.UnderstandVO;
import com.hsmty.cmr.domain.strategy.service.StrategyService;
import com.hsmty.cmr.domain.task.TaskExecutor;
import com.hsmty.cmr.domain.task.TaskService;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.file.FileService;
import com.hsmty.cmr.operator.OperatorClient;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrIndexTableDef.CMR_INDEX;
import static com.hsmty.cmr.domain.search.entity.table.CmrResultItemTableDef.CMR_RESULT_ITEM;
import static com.hsmty.cmr.domain.search.entity.table.CmrSearchHistoryTableDef.CMR_SEARCH_HISTORY;

/**
 * 检索服务：407 澄清代理、408 检索编排（策略快照+索引选择+SRH/结果集/任务落库）、
 * 采用/废弃、点击埋点、检索历史、再次执行
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final SearchHistoryMapper historyMapper;
    private final ResultItemMapper resultItemMapper;
    private final ClickEventMapper clickEventMapper;
    private final IndexMapper indexMapper;
    private final DatasetMapper datasetMapper;
    private final StrategyService strategyService;
    private final OperatorClient operatorClient;
    private final TaskService taskService;
    private final TaskExecutor taskExecutor;
    private final FileService fileService;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    /** 407 查询理解代理：needClarify=true 时前端展示澄清浮层 */
    public UnderstandVO understand(UnderstandDTO dto) {
        BizException.assertTrue(blank(dto.getQuery()) && blank(dto.getQueryFile()),
                "query 与 queryFile 必须二选一");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uniqueId", "q_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        if (!blank(dto.getQuery())) {
            payload.put("query", dto.getQuery());
        } else {
            fileService.resolve(dto.getQueryFile());
            payload.put("queryFile", dto.getQueryFile());
        }
        JsonNode data = operatorClient.understand(payload);
        List<String> options = new ArrayList<>();
        data.path("clarifyOptions").forEach(o -> options.add(o.asText()));
        return UnderstandVO.builder()
                .needClarify(data.path("needClarify").asBoolean(false))
                .clarifyOptions(options)
                .queryModality(textOrNull(data.path("queryModality").asText(null)))
                .hint(textOrNull(data.path("hint").asText(null)))
                .queryFile(textOrNull(data.path("queryFile").asText(null)))
                .build();
    }

    /** 执行检索：同步跑 408（引擎侧失败自动重试 ≤N），结果集快照落库 */
    public SearchResultVO execute(SearchExecuteDTO dto) {
        BizException.assertTrue(blank(dto.getQuery()) && blank(dto.getQueryFile()),
                "query 与 queryFile 必须二选一");
        if (!blank(dto.getQueryFile())) {
            fileService.resolve(dto.getQueryFile());
        }
        List<String> targetModalities = normalizedTargetModalities(dto.getTargetModalities());
        String targetModality = targetModalities == null ? null : targetModalities.get(0);

        // 策略：显式参数覆盖场景生效策略
        StrategyService.EffectiveStrategy effective = strategyService.effectiveOf(dto.getScene());
        Map<String, Integer> weights = dto.getWeights() != null && !dto.getWeights().isEmpty()
                ? dto.getWeights() : effective.weights();
        if (dto.getWeights() != null && !dto.getWeights().isEmpty()) {
            int sum = weights.values().stream().mapToInt(Integer::intValue).sum();
            BizException.assertTrue(sum != 100, "显式权重合计必须为 100，当前 " + sum);
        }
        boolean rerank = Boolean.TRUE.equals(dto.getRerank() != null ? dto.getRerank() : effective.rerank());
        int topK = dto.getTopK() != null ? dto.getTopK() : effective.topK();

        // 索引：场景匹配的 active 索引，兜底最新 active
        CmrIndex index = activeIndexOf(dto.getScene());
        BizException.assertNull(index, "暂无可检索索引，请先完成建库并投产");

        // 先落检索历史（SRH）与任务记录，再执行（每次检索必有记录，失败也留痕）
        String queryModality = !blank(dto.getQueryFile()) ? modalityOf(dto.getQueryFile()) : "text";
        CmrSearchHistory history = new CmrSearchHistory();
        history.setId(idWorker.next());
        history.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_SEARCH));
        history.setResultSetId("PENDING-" + history.getBizNo());
        history.setQueryText(!blank(dto.getClarifiedQuery()) ? dto.getClarifiedQuery() : dto.getQuery());
        history.setQueryFile(dto.getQueryFile());
        history.setQueryModality(queryModality);
        history.setTargetModality(targetModality);
        history.setClarified(Boolean.TRUE.equals(dto.getClarified()));
        history.setScene(dto.getScene());
        history.setStrategyVersionId(effective.versionId());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("weights", weights);
        snapshot.put("rerank", rerank);
        snapshot.put("topK", topK);
        history.setStrategySnapshotJson(JsonUtils.write(snapshot));
        history.setItemCount(0);
        history.setAdoptStatus(CmrSearchHistory.ADOPT_UNADOPTED);
        history.setUserId(AuthContext.userId());
        history.setDelFlag("F");
        history.setCreateTime(LocalDateTime.now());
        historyMapper.insert(history, true);

        CmrTask task = taskService.create(CmrTask.TYPE_SEARCH, history.getId(),
                Map.of("query", history.getQueryText() == null ? "" : history.getQueryText(),
                        "indexDir", index.getIndexDir()));
        history.setTaskId(task.getId());
        historyMapper.update(history);

        taskExecutor.runSyncWithRetry(task, t -> doSearch(t, history, index, targetModalities, weights, rerank, topK));
        // 任务已按失败处置落库；检索历史保留零结果记录，向用户返回原因
        CmrTask done = taskService.requireTask(task.getId());
        if (!CmrTask.STATUS_DONE.equals(done.getStatus())) {
            throw new BizException("检索失败（" + done.getErrorMsg() + "），已记录任务 "
                    + done.getBizNo() + "，可在任务管理查看处置进展");
        }
        return assemble(history);
    }

    private void doSearch(CmrTask task, CmrSearchHistory history, CmrIndex index,
                          List<String> targetModalities, Map<String, Integer> weights,
                          boolean rerank, int topK) {
        taskService.updateStep(task, "search");
        Map<String, Object> payload = new LinkedHashMap<>();
        if (!blank(history.getQueryText())) {
            payload.put("query", history.getQueryText());
        }
        if (!blank(history.getQueryFile())) {
            payload.put("queryFile", history.getQueryFile());
        }
        payload.put("indexDir", index.getIndexDir());
        if (targetModalities != null) {
            payload.put("targetModalities", targetModalities);
        }
        payload.put("topK", topK);
        payload.put("weights", weights);
        payload.put("rerank", rerank);
        try {
            long start = System.currentTimeMillis();
            JsonNode data = operatorClient.search(payload);
            int elapsed = data.path("elapsedMs").asInt((int) (System.currentTimeMillis() - start));

            history.setResultSetId(data.path("resultSetId").asText("RST-" + history.getBizNo()));
            history.setElapsedMs(elapsed);
            List<CmrResultItem> items = new ArrayList<>();
            for (JsonNode node : data.path("items")) {
                CmrResultItem item = new CmrResultItem();
                item.setId(idWorker.next());
                item.setSearchId(history.getId());
                item.setResultSetId(history.getResultSetId());
                item.setRank(node.path("rank").asInt(items.size() + 1));
                item.setItemId(textOrNull(node.path("itemId").asText(null)));
                item.setModality(textOrNull(node.path("modality").asText(null)));
                item.setScore(node.path("score").isNumber() ? node.path("score").decimalValue() : BigDecimal.ZERO);
                item.setSourceFile(textOrNull(node.path("sourceFile").asText(null)));
                item.setLocatorJson(node.has("locator") ? JsonUtils.writeQuietly(node.path("locator")) : null);
                item.setText(textOrNull(node.path("text").asText(null)));
                item.setClicked(false);
                item.setDelFlag("F");
                item.setCreateTime(LocalDateTime.now());
                items.add(item);
            }
            history.setItemCount(items.size());
            historyMapper.update(history);
            for (CmrResultItem item : items) {
                resultItemMapper.insert(item, true);
            }
            taskService.log(task.getId(), "search", "ok", data,
                    "检索完成 items=" + items.size() + " elapsedMs=" + elapsed);
        } catch (Exception e) {
            taskService.log(task.getId(), "search", "fail", null, e.getMessage());
            throw e instanceof RuntimeException re ? re : new BizException(e.getMessage(), e);
        }
    }

    /** 结果集回看（P2 刷新/P9 查看） */
    public HistoryDetailVO resultsOf(String resultSetId) {
        CmrSearchHistory history = historyOfResultSet(resultSetId);
        return detail(history.getId());
    }

    private CmrSearchHistory historyOfResultSet(String resultSetId) {
        CmrSearchHistory history = historyMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.RESULT_SET_ID.eq(resultSetId)));
        BizException.assertNull(history, "结果集不存在");
        return history;
    }

    /** 采用/废弃：一次性终态，二者互斥不可逆 */
    @Transactional(rollbackFor = Exception.class)
    public void adopt(String resultSetId, boolean adopted) {
        CmrSearchHistory history = historyMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.RESULT_SET_ID.eq(resultSetId)));
        BizException.assertNull(history, "结果集不存在");
        BizException.assertTrue(!CmrSearchHistory.ADOPT_UNADOPTED.equals(history.getAdoptStatus()),
                "当前状态不可变更（仅未采用可操作）");
        history.setAdoptStatus(adopted ? CmrSearchHistory.ADOPT_ADOPTED : CmrSearchHistory.ADOPT_DISCARDED);
        historyMapper.update(history);
    }

    /** 条目点击埋点：事件表 + 条目 clicked 冗余 */
    @Transactional(rollbackFor = Exception.class)
    public void click(String resultSetId, Integer rank) {
        CmrSearchHistory history = historyMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.RESULT_SET_ID.eq(resultSetId)));
        BizException.assertNull(history, "结果集不存在");
        CmrResultItem item = resultItemMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_RESULT_ITEM.SEARCH_ID.eq(history.getId()).and(CMR_RESULT_ITEM.RANK.eq(rank))));
        BizException.assertNull(item, "条目不存在: rank=" + rank);

        CmrClickEvent event = new CmrClickEvent();
        event.setId(idWorker.next());
        event.setSearchId(history.getId());
        event.setResultRank(rank);
        event.setItemId(item.getItemId());
        event.setUserId(AuthContext.userId());
        event.setClickedTime(LocalDateTime.now());
        event.setDelFlag("F");
        event.setCreateTime(LocalDateTime.now());
        clickEventMapper.insert(event, true);

        item.setClicked(true);
        resultItemMapper.update(item);
    }

    /** 检索历史分页（P9：query 模糊/查询与目标模态/采用状态/零结果） */
    public PageResult<HistoryVO> page(PageQuery query, String keyword, String modality,
                                      String targetModality, String adoptStatus, Boolean zeroResult) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_SEARCH_HISTORY);
        if (!blank(keyword)) {
            qw.where(CMR_SEARCH_HISTORY.QUERY_TEXT.like(keyword));
        }
        if (!blank(modality)) {
            qw.where(CMR_SEARCH_HISTORY.QUERY_MODALITY.eq(modality));
        }
        if (!blank(targetModality)) {
            qw.where(CMR_SEARCH_HISTORY.TARGET_MODALITY.eq(targetModality));
        }
        if (!blank(adoptStatus)) {
            qw.where(CMR_SEARCH_HISTORY.ADOPT_STATUS.eq(adoptStatus));
        }
        if (Boolean.TRUE.equals(zeroResult)) {
            qw.where(CMR_SEARCH_HISTORY.ITEM_COUNT.eq(0));
        }
        qw.orderBy(CMR_SEARCH_HISTORY.ID.desc());
        Page<CmrSearchHistory> page = historyMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        return PageResult.of(page.getRecords().stream().map(HistoryVO::from).toList(),
                page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    public HistoryDetailVO detail(Long id) {
        CmrSearchHistory history = historyMapper.selectOneById(id);
        BizException.assertNull(history, "检索历史不存在");
        List<ResultItemVO> items = resultItemMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_RESULT_ITEM.SEARCH_ID.eq(id))
                        .orderBy(CMR_RESULT_ITEM.RANK.asc()))
                .stream().map(this::toItemVO).toList();
        return HistoryDetailVO.builder()
                .history(HistoryVO.from(history))
                .strategySnapshotJson(history.getStrategySnapshotJson())
                .items(items)
                .build();
    }

    /** 按结果集重跑：P2 错误态可从 resultSetId 找到原 SRH 并重放 */
    public SearchResultVO rerunByResultSetId(String resultSetId) {
        CmrSearchHistory history = historyOfResultSet(resultSetId);
        return rerun(history.getId());
    }

    /** 再次执行：原参数重放（新 SRH，不复用旧结果） */
    public SearchResultVO rerun(Long id) {
        CmrSearchHistory history = historyMapper.selectOneById(id);
        BizException.assertNull(history, "检索历史不存在");
        SearchExecuteDTO dto = new SearchExecuteDTO();
        dto.setQuery(history.getQueryText());
        dto.setQueryFile(history.getQueryFile());
        dto.setScene(history.getScene());
        dto.setClarified(history.getClarified());
        if (!blank(history.getTargetModality())) {
            dto.setTargetModalities(List.of(history.getTargetModality()));
        }
        Map<String, Object> snapshot = JsonUtils.read(
                history.getStrategySnapshotJson() == null ? "{}" : history.getStrategySnapshotJson(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        if (snapshot.get("topK") instanceof Number n) {
            dto.setTopK(n.intValue());
        }
        if (snapshot.get("rerank") instanceof Boolean rerank) {
            dto.setRerank(rerank);
        }
        if (snapshot.get("weights") instanceof Map<?, ?> sourceWeights) {
            Map<String, Integer> weights = new LinkedHashMap<>();
            sourceWeights.forEach((key, value) -> {
                if (key != null && value instanceof Number number) {
                    weights.put(String.valueOf(key), number.intValue());
                }
            });
            if (!weights.isEmpty()) {
                dto.setWeights(weights);
            }
        }
        return execute(dto);
    }

    /** 按场景取激活索引：场景匹配优先，兜底最新激活 */
    private CmrIndex activeIndexOf(String scene) {
        List<CmrIndex> actives = indexMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_INDEX.STATUS.eq(CmrIndex.STATUS_ACTIVE))
                .orderBy(CMR_INDEX.ACTIVATED_TIME.desc()));
        if (actives.isEmpty()) {
            return null;
        }
        if (!blank(scene)) {
            Map<Long, String> sceneById = new LinkedHashMap<>();
            for (CmrDataset ds : datasetMapper.selectAll()) {
                sceneById.put(ds.getId(), ds.getScene());
            }
            for (CmrIndex idx : actives) {
                if (scene.equals(sceneById.get(idx.getDatasetId()))) {
                    return idx;
                }
            }
        }
        return actives.get(0);
    }

    private SearchResultVO assemble(CmrSearchHistory history) {
        List<ResultItemVO> items = resultItemMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_RESULT_ITEM.SEARCH_ID.eq(history.getId()))
                        .orderBy(CMR_RESULT_ITEM.RANK.asc()))
                .stream().map(this::toItemVO).toList();
        Map<String, Object> strategy = JsonUtils.read(
                history.getStrategySnapshotJson() == null ? "{}" : history.getStrategySnapshotJson(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        return SearchResultVO.builder()
                .id(history.getId())
                .srhNo(history.getBizNo())
                .resultSetId(history.getResultSetId())
                .taskId(history.getTaskId())
                .taskBizNo(taskService.bizNoOf(history.getTaskId()))
                .queryModality(history.getQueryModality())
                .targetModality(history.getTargetModality())
                .itemCount(history.getItemCount())
                .elapsedMs(history.getElapsedMs())
                .strategy(strategy)
                .items(items)
                .build();
    }

    private ResultItemVO toItemVO(CmrResultItem e) {
        return ResultItemVO.builder()
                .id(e.getId()).rank(e.getRank()).itemId(e.getItemId()).modality(e.getModality())
                .score(e.getScore()).sourceFile(e.getSourceFile()).locatorJson(e.getLocatorJson())
                .text(e.getText()).clicked(e.getClicked())
                .build();
    }

    private List<String> normalizedTargetModalities(List<String> targetModalities) {
        if (targetModalities == null) {
            return null;
        }
        BizException.assertTrue(targetModalities.isEmpty(), "目标模态不能为空");
        boolean invalid = targetModalities.stream()
                .anyMatch(value -> value == null || value.isBlank()
                        || !Set.of("text", "image", "audio", "video").contains(value));
        BizException.assertTrue(invalid, "目标模态只能是 text/image/audio/video");
        return List.copyOf(targetModalities);
    }

    private String modalityOf(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
            return "image";
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")
                || lower.endsWith(".ogg") || lower.endsWith(".m4a")) {
            return "audio";
        }
        if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov")
                || lower.endsWith(".mkv") || lower.endsWith(".webm")) {
            return "video";
        }
        return "text";
    }

    private String textOrNull(String s) {
        return s == null || s.isBlank() || "null".equals(s) ? null : s;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
