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
import com.hsmty.cmr.domain.dataset.mapper.DatasourceBlacklistMapper;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.search.dto.SearchExecuteDTO;
import com.hsmty.cmr.domain.search.dto.UnderstandDTO;
import com.hsmty.cmr.domain.search.entity.CmrClickEvent;
import com.hsmty.cmr.domain.search.entity.CmrResultItem;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.operation.mapper.BlockedItemMapper;
import com.hsmty.cmr.domain.search.mapper.ClickEventMapper;
import com.hsmty.cmr.domain.search.mapper.FavoriteMapper;
import com.hsmty.cmr.domain.search.mapper.ItemVoteMapper;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrIndexTableDef.CMR_INDEX;
import static com.hsmty.cmr.domain.operation.entity.table.CmrBlockedItemTableDef.CMR_BLOCKED_ITEM;
import static com.hsmty.cmr.domain.search.entity.table.CmrFavoriteTableDef.CMR_FAVORITE;
import static com.hsmty.cmr.domain.search.entity.table.CmrItemVoteTableDef.CMR_ITEM_VOTE;
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

    /** 摘要保留 30 天（数据流 4.x 检索日志口径） */
    private static final int SUMMARY_KEEP_DAYS = 30;

    /** 文本查询少于该字数且未澄清时拒绝检索（P1 澄清规则） */
    private static final int CLARIFY_MIN_CHARS = 4;

    private final SearchHistoryMapper historyMapper;
    private final ResultItemMapper resultItemMapper;
    private final ClickEventMapper clickEventMapper;
    private final ItemVoteMapper itemVoteMapper;
    private final FavoriteMapper favoriteMapper;
    private final BlockedItemMapper blockedItemMapper;
    private final IndexMapper indexMapper;
    private final DatasetMapper datasetMapper;
    private final DatasourceBlacklistMapper blacklistMapper;
    private final StrategyService strategyService;
    private final OperatorClient operatorClient;
    private final TaskService taskService;
    private final TaskExecutor taskExecutor;
    private final FileService fileService;
    private final ItemMetaService itemMetaService;
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
        data.path("clarifyOptions").forEach(o -> options.add(clarifyOptionText(o)));
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
        // P1 澄清规则：文本查询少于 4 字且未澄清 → 拒绝；文件查询不走澄清
        if (blank(dto.getQueryFile()) && !Boolean.TRUE.equals(dto.getClarified())) {
            String effectiveQuery = blank(dto.getClarifiedQuery()) ? dto.getQuery() : dto.getClarifiedQuery();
            int length = effectiveQuery == null ? 0 : effectiveQuery.trim().length();
            BizException.assertTrue(length < CLARIFY_MIN_CHARS,
                    "搜索词少于 " + CLARIFY_MIN_CHARS + " 个字，请先完成澄清后再检索");
        }
        List<String> targetModalities = normalizedTargetModalities(dto.getTargetModalities());
        String targetModality = targetModalities == null ? null : targetModalities.get(0);

        // 策略：显式选定策略 > 场景生效策略；显式参数再覆盖策略参数
        StrategyService.EffectiveStrategy effective = dto.getStrategyId() != null
                ? strategyService.effectiveById(dto.getStrategyId())
                : strategyService.effectiveOf(dto.getScene());
        Map<String, Integer> weights = dto.getWeights() != null && !dto.getWeights().isEmpty()
                ? dto.getWeights() : effective.weights();
        if (dto.getWeights() != null && !dto.getWeights().isEmpty()) {
            int sum = weights.values().stream().mapToInt(Integer::intValue).sum();
            BizException.assertTrue(sum != 100, "显式权重合计必须为 100，当前 " + sum);
        }
        boolean rerank = Boolean.TRUE.equals(dto.getRerank() != null ? dto.getRerank() : effective.rerank());
        int topK = dto.getTopK() != null ? dto.getTopK() : effective.topK();
        BigDecimal threshold = dto.getThreshold() != null ? dto.getThreshold() : effective.threshold();
        BizException.assertTrue(threshold == null, "阈值不能为空");

        // 索引：场景匹配的 active 索引，兜底最新 active
        CmrIndex index = activeIndexOf(dto.getScene());
        BizException.assertNull(index, "暂无可检索索引，请先完成建库并投产");

        // 先落检索历史（SRH）与任务记录，再执行（每次检索必有记录，失败也留痕）
        String queryModality = !blank(dto.getQueryFile()) ? modalityOf(dto.getQueryFile()) : "text";
        CmrSearchHistory history = new CmrSearchHistory();
        history.setId(idWorker.next());
        history.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_SEARCH));
        history.setIndexId(index.getId());
        history.setResultSetId("PENDING-" + history.getBizNo());
        history.setQueryText(!blank(dto.getClarifiedQuery()) ? dto.getClarifiedQuery() : dto.getQuery());
        history.setQueryFile(dto.getQueryFile());
        history.setQueryModality(queryModality);
        history.setTargetModality(targetModality);
        history.setClarified(Boolean.TRUE.equals(dto.getClarified()));
        history.setScene(dto.getScene());
        history.setStrategyVersionId(effective.versionId());
        history.setStrategyNo(effective.strategyNo());
        history.setStrategyVersion(effective.versionNo());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("weights", weights);
        snapshot.put("rerank", rerank);
        snapshot.put("topK", topK);
        snapshot.put("threshold", threshold);
        snapshot.put("strategyNo", effective.strategyNo());
        snapshot.put("strategyVersion", effective.versionNo());
        history.setStrategySnapshotJson(JsonUtils.write(snapshot));
        history.setThreshold(threshold);
        history.setItemCount(0);
        history.setAdoptStatus(CmrSearchHistory.ADOPT_UNADOPTED);
        history.setSealed("F");
        history.setSummaryExpireTime(LocalDateTime.now().plusDays(SUMMARY_KEEP_DAYS));
        history.setUserId(AuthContext.userId());
        history.setOperatorId(AuthContext.userId());
        history.setOperatorName(currentOperatorName());
        history.setDelFlag("F");
        history.setCreateTime(LocalDateTime.now());
        historyMapper.insert(history, true);

        CmrTask task = taskService.create(CmrTask.TYPE_SEARCH, history.getId(),
                Map.of("query", history.getQueryText() == null ? "" : history.getQueryText(),
                        "indexDir", index.getIndexDir()));
        history.setTaskId(task.getId());
        historyMapper.update(history);

        taskExecutor.runSyncWithRetry(task, t -> doSearch(t, history, index, targetModalities, weights, rerank, topK, threshold));
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
                          boolean rerank, int topK, BigDecimal threshold) {
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
        payload.put("threshold", threshold);
        try {
            long start = System.currentTimeMillis();
            JsonNode data = operatorClient.search(payload);
            int elapsed = data.path("elapsedMs").asInt((int) (System.currentTimeMillis() - start));

            history.setResultSetId(data.path("resultSetId").asText("RST-" + history.getBizNo()));
            history.setElapsedMs(elapsed);
            // 质量等级继承 P4 接入评定：同一索引属于同一数据集，本次检索统一取值
            String qualityGrade = datasetGrade(index.getDatasetId());
            List<CmrResultItem> items = new ArrayList<>();
            for (JsonNode node : data.path("items")) {
                CmrResultItem item = new CmrResultItem();
                item.setId(idWorker.next());
                item.setSearchId(history.getId());
                item.setResultSetId(history.getResultSetId());
                item.setRank(node.path("rank").asInt(items.size() + 1));
                item.setItemId(textOrNull(node.path("itemId").asText(null)));
                item.setItemName(textOrNull(node.path("name").asText(null)));
                item.setSource(textOrNull(node.path("source").asText(null)));
                item.setModality(textOrNull(node.path("modality").asText(null)));
                item.setScore(node.path("score").isNumber() ? node.path("score").decimalValue() : BigDecimal.ZERO);
                item.setAlignScore(node.path("alignScore").isNumber()
                        ? node.path("alignScore").decimalValue() : null);
                item.setQualityGrade(qualityGrade);
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
                                      String targetModality, String adoptStatus, String taskStatus,
                                      Boolean zeroResult) {
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
        if (!blank(taskStatus)) {
            qw.where("task_id IN (SELECT id FROM cmr_task WHERE status = ? AND del_flag = 'F')", taskStatus);
        }
        if (Boolean.TRUE.equals(zeroResult)) {
            qw.where(CMR_SEARCH_HISTORY.ITEM_COUNT.eq(0));
        }
        qw.orderBy(CMR_SEARCH_HISTORY.ID.desc());
        Page<CmrSearchHistory> page = historyMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        return PageResult.of(page.getRecords().stream()
                        .map(h -> HistoryVO.from(h, taskService.bizNoOf(h.getTaskId()), taskService.statusOf(h.getTaskId())))
                        .toList(),
                page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    public HistoryDetailVO detail(Long id) {
        CmrSearchHistory history = historyMapper.selectOneById(id);
        BizException.assertNull(history, "检索历史不存在");
        List<ResultItemVO> items = itemsOf(history);
        return HistoryDetailVO.builder()
                .history(HistoryVO.from(history, taskService.bizNoOf(history.getTaskId()),
                        taskService.statusOf(history.getTaskId())))
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
        return rerun(id, null, null);
    }

    /** 评测重放：可指定策略并归属评测 */
    public SearchResultVO rerun(Long id, Long strategyId, Long evalId) {
        CmrSearchHistory history = historyMapper.selectOneById(id);
        BizException.assertNull(history, "检索历史不存在");
        SearchExecuteDTO dto = new SearchExecuteDTO();
        if (strategyId != null) {
            dto.setStrategyId(strategyId);
        }
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
        if (snapshot.get("threshold") instanceof Number number) {
            dto.setThreshold(new BigDecimal(number.toString()));
        }
        SearchResultVO result = execute(dto);
        // 标记重放来源，供评测与复盘串链
        CmrSearchHistory replay = historyMapper.selectOneById(result.getId());
        if (replay != null) {
            replay.setReplayOfTaskNo(history.getBizNo());
            replay.setEvalId(evalId);
            historyMapper.update(replay);
        }
        return result;
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
        List<ResultItemVO> items = itemsOf(history);
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
                // 命中条数以屏蔽过滤后的实际下发条数为准（库表设计：结果条数）
                .itemCount(items.size())
                .elapsedMs(history.getElapsedMs())
                .strategy(strategy)
                .items(items)
                .build();
    }

    private List<ResultItemVO> itemsOf(CmrSearchHistory history) {
        List<CmrResultItem> entities = resultItemMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_RESULT_ITEM.SEARCH_ID.eq(history.getId()))
                        .orderBy(CMR_RESULT_ITEM.RANK.asc()));
        List<String> itemIds = entities.stream()
                .map(CmrResultItem::getItemId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        // 屏蔽条目与黑名单来源不再下发给检索端（屏蔽列表只影响展示，不影响历史快照）
        Set<String> blocked = blockedItemIds(itemIds);
        Set<String> blacklistedSources = blacklistedSources();
        Map<String, ItemMetaService.ItemMeta> metas = itemMetaService.lookup(history.getIndexId(), itemIds);
        Map<String, String> votes = votesOf(history.getBizNo());
        Set<String> favorites = favoriteItemIds();
        return entities.stream()
                .filter(item -> item.getItemId() == null || !blocked.contains(item.getItemId()))
                .filter(item -> !blacklistedSources.contains(item.getSource()))
                .map(item -> toItemVO(item, metas.get(item.getItemId()),
                        votes.get(item.getItemId()), favorites.contains(item.getItemId())))
                .toList();
    }

    /** 黑名单：来源名 + 数据集 ID 两路匹配，命中即整源排除 */
    private Set<String> blacklistedSources() {
        Set<String> names = new HashSet<>();
        blacklistMapper.selectListByQuery(QueryWrapper.create()).forEach(b -> {
            if (!blank(b.getSourceName())) {
                names.add(b.getSourceName());
            }
            if (!blank(b.getDatasetId())) {
                names.add(b.getDatasetId());
            }
        });
        return names;
    }

    private Set<String> blockedItemIds(List<String> itemIds) {
        if (itemIds.isEmpty()) {
            return Set.of();
        }
        return blockedItemMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_BLOCKED_ITEM.ITEM_ID.in(itemIds)))
                .stream().map(com.hsmty.cmr.domain.operation.entity.CmrBlockedItem::getItemId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Map<String, String> votesOf(String taskNo) {
        if (blank(taskNo)) {
            return Map.of();
        }
        Long userId = AuthContext.userId();
        if (userId == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        itemVoteMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_ITEM_VOTE.TASK_NO.eq(taskNo).and(CMR_ITEM_VOTE.USER_ID.eq(userId))))
                .forEach(v -> result.put(v.getItemId(), v.getVote()));
        return result;
    }

    private Set<String> favoriteItemIds() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            return Set.of();
        }
        return favoriteMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_FAVORITE.USER_ID.eq(userId)))
                .stream().map(com.hsmty.cmr.domain.search.entity.CmrFavorite::getItemId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private ResultItemVO toItemVO(CmrResultItem e, ItemMetaService.ItemMeta meta,
                                  String vote, boolean favorited) {
        boolean deleted = meta == null;
        String name = e.getItemName() != null ? e.getItemName()
                : (deleted ? "已删除" : meta.name());
        return ResultItemVO.builder()
                .id(e.getId()).rank(e.getRank()).itemId(e.getItemId())
                .name(name)
                .source(e.getSource() != null ? e.getSource() : (deleted ? null : meta.sourceFile()))
                .modality(e.getModality()).score(e.getScore()).alignScore(e.getAlignScore())
                .qualityGrade(e.getQualityGrade())
                .vote(vote).favorited(favorited)
                .sourceFile(deleted ? null : meta.sourceFile())
                .locatorJson(deleted ? null : meta.locatorJson())
                .text(deleted ? null : meta.text())
                .clicked(e.getClicked()).deleted(deleted)
                .build();
    }

    /** 质量等级继承 P4 402 评定分：≥0.90 A / ≥0.80 B / ≥0.70 C / 其余 D；无评定分返回 null */
static String qualityGradeOf(BigDecimal score) {
    if (score == null) {
        return null;
    }
    if (score.compareTo(new BigDecimal("0.90")) >= 0) {
        return "A";
    }
    if (score.compareTo(new BigDecimal("0.80")) >= 0) {
        return "B";
    }
    if (score.compareTo(new BigDecimal("0.70")) >= 0) {
        return "C";
    }
    return "D";
}

private String datasetGrade(Long datasetId) {
    if (datasetId == null) {
        return null;
    }
    CmrDataset dataset = datasetMapper.selectOneById(datasetId);
    return dataset == null ? null : qualityGradeOf(dataset.getScore());
}

/** 赞/踩：同一用户对同一任务同一条目只保留一条，重复同向即取消 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> vote(String taskNo, String itemId, String vote) {
        BizException.assertTrue(blank(taskNo) || blank(itemId), "任务号与条目不能为空");
        BizException.assertTrue(!Set.of("up", "down", "none").contains(vote),
                "投票只能是 up/down/none");
        CmrSearchHistory history = requireHistoryByBizNo(taskNo);
        BizException.assertTrue("T".equals(history.getSealed()), "任务已封存，赞/踩不可再改");
        Long userId = AuthContext.userId();
        BizException.assertNull(userId, "未登录");

        com.hsmty.cmr.domain.search.entity.CmrItemVote existing = itemVoteMapper.selectOneByQuery(
                QueryWrapper.create().where(CMR_ITEM_VOTE.TASK_NO.eq(taskNo)
                        .and(CMR_ITEM_VOTE.ITEM_ID.eq(itemId))
                        .and(CMR_ITEM_VOTE.USER_ID.eq(userId))));
        String effectiveVote = vote;
        if ("none".equals(vote)) {
            if (existing != null) {
                itemVoteMapper.deleteById(existing.getId());
            }
            effectiveVote = null;
        } else if (existing == null) {
            com.hsmty.cmr.domain.search.entity.CmrItemVote entity =
                    new com.hsmty.cmr.domain.search.entity.CmrItemVote();
            entity.setId(idWorker.next());
            entity.setTaskNo(taskNo);
            entity.setSearchId(history.getId());
            entity.setItemId(itemId);
            entity.setVote(vote);
            entity.setUserId(userId);
            entity.setDelFlag("F");
            entity.setCreateTime(LocalDateTime.now());
            itemVoteMapper.insert(entity, true);
        } else if (!vote.equals(existing.getVote())) {
            existing.setVote(vote);
            itemVoteMapper.update(existing);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vote", effectiveVote);
        result.put("upCount", countVotes(history.getId(), "up"));
        result.put("downCount", countVotes(history.getId(), "down"));
        return result;
    }

    private long countVotes(Long searchId, String vote) {
        Long count = itemVoteMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_ITEM_VOTE.SEARCH_ID.eq(searchId).and(CMR_ITEM_VOTE.VOTE.eq(vote))));
        return count == null ? 0 : count;
    }

    /** 收藏/取消收藏（按 用户+条目 唯一） */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(String itemId, String itemName, String source, String taskNo) {
        BizException.assertTrue(blank(itemId), "条目不能为空");
        Long userId = AuthContext.userId();
        BizException.assertNull(userId, "未登录");
        com.hsmty.cmr.domain.search.entity.CmrFavorite existing = favoriteMapper.selectOneByQuery(
                QueryWrapper.create().where(CMR_FAVORITE.USER_ID.eq(userId)
                        .and(CMR_FAVORITE.ITEM_ID.eq(itemId))));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return false;
        }
        com.hsmty.cmr.domain.search.entity.CmrFavorite entity =
                new com.hsmty.cmr.domain.search.entity.CmrFavorite();
        entity.setId(idWorker.next());
        entity.setUserId(userId);
        entity.setItemId(itemId);
        entity.setItemName(itemName);
        entity.setSource(source);
        entity.setTaskNo(taskNo);
        entity.setDelFlag("F");
        entity.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(entity, true);
        return true;
    }

    /** 我的收藏 */
    public List<Map<String, Object>> favorites() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            return List.of();
        }
        return favoriteMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_FAVORITE.USER_ID.eq(userId))
                        .orderBy(CMR_FAVORITE.ID.desc()))
                .stream().map(f -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", f.getId());
                    item.put("itemId", f.getItemId());
                    item.put("itemName", f.getItemName());
                    item.put("source", f.getSource());
                    item.put("taskNo", f.getTaskNo());
                    item.put("createTime", f.getCreateTime());
                    return item;
                }).toList();
    }

    /** 封存：锁定赞/踩，作为评测与复盘的固定样本 */
    @Transactional(rollbackFor = Exception.class)
    public void seal(String taskNo) {
        CmrSearchHistory history = requireHistoryByBizNo(taskNo);
        BizException.assertTrue("T".equals(history.getSealed()), "任务已封存");
        history.setSealed("T");
        historyMapper.update(history);
        log.info("检索任务封存: {}", taskNo);
    }

    /** 搜索结果摘要（P3）：Top-20 条目 + 赞/踩，30 天后过期 */
    public Map<String, Object> summary(String taskNo) {
        CmrSearchHistory history = requireHistoryByBizNo(taskNo);
        List<ResultItemVO> delivered = itemsOf(history);
        List<ResultItemVO> items = delivered.stream().limit(20).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", history.getBizNo());
        result.put("searchId", history.getId());
        result.put("resultSetId", history.getResultSetId());
        result.put("queryText", history.getQueryText());
        result.put("queryModality", history.getQueryModality());
        result.put("targetModality", history.getTargetModality());
        result.put("operatorName", history.getOperatorName());
        result.put("strategyNo", history.getStrategyNo());
        result.put("strategyVersion", history.getStrategyVersion());
        result.put("threshold", history.getThreshold());
        // 命中条数以屏蔽过滤后的实际下发条数为准
        result.put("itemCount", delivered.size());
        result.put("sealed", "T".equals(history.getSealed()));
        result.put("createTime", history.getCreateTime());
        result.put("expireTime", history.getSummaryExpireTime());
        result.put("expired", history.getSummaryExpireTime() != null
                && history.getSummaryExpireTime().isBefore(LocalDateTime.now()));
        result.put("createId", history.getCreateId());
        result.put("upCount", countVotes(history.getId(), "up"));
        result.put("downCount", countVotes(history.getId(), "down"));
        result.put("items", items);
        return result;
    }

    /** 搜索行为流水（P6）：任务级分页，含赞踩计数与失败状态 */
    public PageResult<Map<String, Object>> behaviors(PageQuery query, String keyword,
                                                     String operatorName, Boolean zeroResult,
                                                     LocalDateTime from, LocalDateTime to) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_SEARCH_HISTORY);
        if (!blank(keyword)) {
            qw.where(CMR_SEARCH_HISTORY.QUERY_TEXT.like(keyword));
        }
        if (!blank(operatorName)) {
            qw.where(CMR_SEARCH_HISTORY.OPERATOR_NAME.like(operatorName));
        }
        if (Boolean.TRUE.equals(zeroResult)) {
            qw.where(CMR_SEARCH_HISTORY.ITEM_COUNT.eq(0));
        }
        if (from != null) {
            qw.where(CMR_SEARCH_HISTORY.CREATE_TIME.ge(from));
        }
        if (to != null) {
            qw.where(CMR_SEARCH_HISTORY.CREATE_TIME.le(to));
        }
        qw.orderBy(CMR_SEARCH_HISTORY.ID.desc());
        Page<CmrSearchHistory> page = historyMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        List<Map<String, Object>> records = page.getRecords().stream().map(h -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", h.getId());
            item.put("taskNo", h.getBizNo());
            item.put("resultSetId", h.getResultSetId());
            item.put("taskId", h.getTaskId());
            item.put("taskBizNo", taskService.bizNoOf(h.getTaskId()));
            item.put("taskStatus", taskService.statusOf(h.getTaskId()));
            item.put("queryText", h.getQueryText());
            item.put("queryModality", h.getQueryModality());
            item.put("targetModality", h.getTargetModality());
            item.put("operatorName", h.getOperatorName());
            item.put("strategyNo", h.getStrategyNo());
            item.put("strategyVersion", h.getStrategyVersion());
            item.put("threshold", h.getThreshold());
            item.put("itemCount", h.getItemCount());
            item.put("elapsedMs", h.getElapsedMs());
            item.put("sealed", "T".equals(h.getSealed()));
            item.put("zeroResult", h.getItemCount() != null && h.getItemCount() == 0);
            item.put("upCount", countVotes(h.getId(), "up"));
            item.put("downCount", countVotes(h.getId(), "down"));
            item.put("createTime", h.getCreateTime());
            return item;
        }).toList();
        return PageResult.of(records, page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /** 屏蔽列表（P4/P7）：登记后不再下发给检索端 */
    public List<Map<String, Object>> blockedItems() {
        return blockedItemMapper.selectListByQuery(QueryWrapper.create()
                        .orderBy(CMR_BLOCKED_ITEM.ID.desc()))
                .stream().map(b -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", b.getId());
                    item.put("itemId", b.getItemId());
                    item.put("itemName", b.getItemName());
                    item.put("source", b.getSource());
                    item.put("reason", b.getReason());
                    item.put("feedbackId", b.getFeedbackId());
                    item.put("createTime", b.getCreateTime());
                    return item;
                }).toList();
    }

    /** 解除屏蔽 */
    @Transactional(rollbackFor = Exception.class)
    public void unblock(Long blockedId) {
        com.hsmty.cmr.domain.operation.entity.CmrBlockedItem entity = blockedItemMapper.selectOneById(blockedId);
        BizException.assertNull(entity, "屏蔽记录不存在");
        blockedItemMapper.deleteById(blockedId);
    }

    /** 登记屏蔽（供反馈处置与结果页直接屏蔽复用） */
    @Transactional(rollbackFor = Exception.class)
    public void blockItem(String itemId, String itemName, String source, String reason, Long feedbackId) {
        BizException.assertTrue(blank(itemId), "条目不能为空");
        Long exists = blockedItemMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_BLOCKED_ITEM.ITEM_ID.eq(itemId)));
        if (exists != null && exists > 0) {
            return;
        }
        com.hsmty.cmr.domain.operation.entity.CmrBlockedItem entity =
                new com.hsmty.cmr.domain.operation.entity.CmrBlockedItem();
        entity.setId(idWorker.next());
        entity.setItemId(itemId);
        entity.setItemName(itemName);
        entity.setSource(source);
        entity.setReason(blank(reason) ? "运营屏蔽" : reason);
        entity.setFeedbackId(feedbackId);
        entity.setDelFlag("F");
        entity.setCreateTime(LocalDateTime.now());
        blockedItemMapper.insert(entity, true);
    }

    private CmrSearchHistory requireHistoryByBizNo(String taskNo) {
        CmrSearchHistory history = historyMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.BIZ_NO.eq(taskNo)));
        BizException.assertNull(history, "检索任务不存在: " + taskNo);
        return history;
    }

    private String currentOperatorName() {
        com.hsmty.cmr.common.auth.LoginUser user = AuthContext.get();
        if (user == null) {
            return null;
        }
        return user.nickname() != null && !user.nickname().isBlank()
                ? user.nickname() : user.username();
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

    /** 澄清选项：407 返回对象时序列化成 JSON 字符串（前端解析后取 query/intent），纯文本保持原样 */
    private String clarifyOptionText(JsonNode option) {
        if (option == null || option.isNull()) {
            return "";
        }
        return option.isObject() ? JsonUtils.writeQuietly(option) : option.asText("");
    }

    private String textOrNull(String s) {
        return s == null || s.isBlank() || "null".equals(s) ? null : s;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
