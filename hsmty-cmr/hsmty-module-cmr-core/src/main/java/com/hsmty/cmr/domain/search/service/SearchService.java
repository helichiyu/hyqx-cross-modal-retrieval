package com.hsmty.cmr.domain.search.service;

import tools.jackson.databind.JsonNode;
import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.auth.AuthContext;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceBlacklistMapper;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvaluation;
import com.hsmty.cmr.domain.evaluation.mapper.EvaluationMapper;
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
import com.hsmty.cmr.domain.search.vo.BehaviorVO;
import com.hsmty.cmr.domain.search.vo.ExcludedItemVO;
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
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
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
import static com.hsmty.cmr.domain.evaluation.entity.table.CmrEvaluationTableDef.CMR_EVALUATION;
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

    /** 文本查询少于该字数且未澄清时拒绝检索（P1 澄清规则） */
    private static final int CLARIFY_MIN_CHARS = 4;

    /** 默认业务场景；未显式传 scene 时优先该场景的激活索引 */
    private static final String DEFAULT_SCENE = "general";

    /** 剔除明细最多返回 100 条（excludedCount 仍为全量） */
    private static final int EXCLUDED_ITEM_LIMIT = 100;

    /** 剔除原因：命中屏蔽条目或来源黑名单 */
    private static final String EXCLUDE_REASON_BLACKLIST = "blacklist";

    private final SearchHistoryMapper historyMapper;
    private final ResultItemMapper resultItemMapper;
    private final ClickEventMapper clickEventMapper;
    private final ItemVoteMapper itemVoteMapper;
    private final FavoriteMapper favoriteMapper;
    private final BlockedItemMapper blockedItemMapper;
    private final IndexMapper indexMapper;
    private final DatasetMapper datasetMapper;
    private final DatasourceBlacklistMapper blacklistMapper;
    private final EvaluationMapper evaluationMapper;
    private final StrategyService strategyService;
    private final OperatorClient operatorClient;
    private final TaskService taskService;
    private final TaskExecutor taskExecutor;
    private final FileService fileService;
    private final ItemMetaService itemMetaService;
    private final CmrProperties properties;
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

        // 索引：场景匹配的 active 索引，兜底最新 active；均无激活索引时回落配置的兜底索引目录
        CmrIndex index = activeIndexOf(dto.getScene());
        if (index == null) {
            String fallbackDir = properties.getSearch().getIndexDir();
            BizException.assertTrue(fallbackDir == null || fallbackDir.isBlank(),
                    "暂无可检索索引，请先完成建库并投产");
            CmrIndex fallback = new CmrIndex();
            fallback.setIndexDir(fallbackDir);
            index = fallback;
        }
        final CmrIndex resolvedIndex = index;

        // 先落检索历史（SRH）与任务记录，再执行（每次检索必有记录，失败也留痕）
        String queryModality = !blank(dto.getQueryFile()) ? modalityOf(dto.getQueryFile()) : "text";
        CmrSearchHistory history = new CmrSearchHistory();
        history.setId(idWorker.next());
        history.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_SEARCH));
        history.setIndexId(resolvedIndex.getId());
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
        // 兜底索引无 cmr_index 记录，回看时按快照里的目录直查 meta.db
        snapshot.put("indexDir", resolvedIndex.getIndexDir());
        history.setStrategySnapshotJson(JsonUtils.write(snapshot));
        history.setThreshold(threshold);
        history.setItemCount(0);
        history.setAdoptStatus(CmrSearchHistory.ADOPT_UNADOPTED);
        history.setSealed("F");
        history.setSummaryExpireTime(LocalDateTime.now().plusDays(properties.getSearch().getSummaryDays()));
        history.setUserId(AuthContext.userId());
        history.setOperatorId(AuthContext.userId());
        history.setOperatorName(currentOperatorName());
        history.setDelFlag("F");
        history.setCreateTime(LocalDateTime.now());
        historyMapper.insert(history, true);

        CmrTask task = taskService.create(CmrTask.TYPE_SEARCH, history.getId(),
                Map.of("query", history.getQueryText() == null ? "" : history.getQueryText(),
                        "indexDir", resolvedIndex.getIndexDir()));
        history.setTaskId(task.getId());
        historyMapper.update(history);

        taskExecutor.runSyncWithRetry(task,
                t -> doSearch(t, history, resolvedIndex, targetModalities, weights, rerank, topK, threshold));
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
            // 质量等级继承 P4 接入评定：同一索引属于同一数据集，本次检索统一取值；兜底索引无归属数据集
            String qualityGrade = index.getDatasetId() == null ? null : datasetGrade(index.getDatasetId());
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
                        .map(h -> HistoryVO.from(h, taskService.bizNoOf(h.getTaskId()),
                                taskService.statusOf(h.getTaskId()), replayTaskNoOf(h.getReplayOfTaskNo())))
                        .toList(),
                page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /**
     * 检索历史详情：结果集条目快照、策略快照与下发前剔除明细。
     *
     * @param id 检索历史 ID
     * @return 详情视图
     * @author cmr
     * @since 2026-11-11
     */
    public HistoryDetailVO detail(Long id) {
        CmrSearchHistory history = historyMapper.selectOneById(id);
        BizException.assertNull(history, "检索历史不存在");
        DeliveredItems delivered = deliveredOf(history);
        return HistoryDetailVO.builder()
                .history(HistoryVO.from(history, taskService.bizNoOf(history.getTaskId()),
                        taskService.statusOf(history.getTaskId()),
                        replayTaskNoOf(history.getReplayOfTaskNo())))
                .strategySnapshotJson(history.getStrategySnapshotJson())
                .items(delivered.items())
                .excludedCount(delivered.excludedCount())
                .excludedItems(delivered.excludedItems())
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
        // 评测重放（指定策略或归属评测）必须让新策略参数生效；仅纯历史重放才沿用原快照
        boolean reuseSnapshot = strategyId == null && evalId == null;
        if (reuseSnapshot) {
            Map<String, Object> snapshot = JsonUtils.read(
                    history.getStrategySnapshotJson() == null ? "{}" : history.getStrategySnapshotJson(),
                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
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
        }
        SearchResultVO result = execute(dto);
        // 标记重放来源，供评测与复盘串链；搜索行为页「重新执行」按 query 归入已有评测
        CmrSearchHistory replay = historyMapper.selectOneById(result.getId());
        if (replay != null) {
            replay.setReplayOfTaskNo(history.getBizNo());
            replay.setEvalId(evalId != null ? evalId : evaluationIdOfQuery(history.getQueryText()));
            historyMapper.update(replay);
        }
        return result;
    }

    /** 重新执行按 query 归入已有评测（数据流 3.6）；文件查询不归集 */
    private Long evaluationIdOfQuery(String queryText) {
        if (blank(queryText)) {
            return null;
        }
        CmrEvaluation evaluation = evaluationMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_EVALUATION.QUERY.eq(queryText))
                .orderBy(CMR_EVALUATION.ID.desc())
                .limit(1));
        return evaluation == null ? null : evaluation.getId();
    }

    /**
     * 按场景取激活索引：场景匹配优先，未指定场景时优先通用场景。
     *
     * <p>不能直接兜底“最新激活”：联调/专项场景（如 e2e）的数据集激活后会排在通用场景之前，
     * 导致默认检索被小样本索引抢占，全量索引不可见。因此 scene 为空时先找 general，
     * 找不到才回退最新激活。
     */
    private CmrIndex activeIndexOf(String scene) {
        List<CmrIndex> actives = indexMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_INDEX.STATUS.eq(CmrIndex.STATUS_ACTIVE))
                .orderBy(CMR_INDEX.ACTIVATED_TIME.desc()));
        if (actives.isEmpty()) {
            return null;
        }
        Map<Long, String> sceneById = new LinkedHashMap<>();
        for (CmrDataset ds : datasetMapper.selectAll()) {
            sceneById.put(ds.getId(), ds.getScene());
        }
        String effectiveScene = blank(scene) ? DEFAULT_SCENE : scene;
        for (CmrIndex idx : actives) {
            if (effectiveScene.equals(sceneById.get(idx.getDatasetId()))) {
                return idx;
            }
        }
        // 兜底只选归属数据集仍存在的索引，避免落到无主的历史 active 索引
        for (CmrIndex idx : actives) {
            if (sceneById.containsKey(idx.getDatasetId())) {
                return idx;
            }
        }
        return null;
    }

    private SearchResultVO assemble(CmrSearchHistory history) {
        List<ResultItemVO> items = itemsOf(history);
        Map<String, Object> strategy = JsonUtils.read(
                history.getStrategySnapshotJson() == null ? "{}" : history.getStrategySnapshotJson(),
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
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
        return deliveredOf(history).items();
    }

    /**
     * 计算本次下发的条目与剔除明细。
     *
     * <p>沿用原黑名单剔除逻辑（屏蔽条目 + 来源黑名单），并在剔除时记录原因；
     * 质量与策略过滤在 408 召回阶段完成，未进入本地快照，当前可见的剔除原因仅为 blacklist。
     *
     * @param history 检索历史
     * @return 下发条目、剔除计数与剔除明细
     * @author cmr
     * @since 2026-11-11
     */
    private DeliveredItems deliveredOf(CmrSearchHistory history) {
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
        Map<String, ItemMetaService.ItemMeta> metas = history.getIndexId() != null
                ? itemMetaService.lookup(history.getIndexId(), itemIds)
                : itemMetaService.lookupByDir(snapshotIndexDir(history), itemIds);
        Map<String, String> votes = votesOf(history.getBizNo());
        Set<String> favorites = favoriteItemIds();
        List<ResultItemVO> items = new ArrayList<>();
        List<ExcludedItemVO> excludedItems = new ArrayList<>();
        int excludedCount = 0;
        for (CmrResultItem item : entities) {
            String reason = excludedReasonOf(item, blocked, blacklistedSources);
            if (reason == null) {
                items.add(toItemVO(item, metas.get(item.getItemId()),
                        votes.get(item.getItemId()), favorites.contains(item.getItemId())));
                continue;
            }
            excludedCount++;
            if (excludedItems.size() < EXCLUDED_ITEM_LIMIT) {
                ItemMetaService.ItemMeta meta = metas.get(item.getItemId());
                excludedItems.add(ExcludedItemVO.builder()
                        .itemId(item.getItemId())
                        .name(item.getItemName() != null ? item.getItemName()
                                : (meta == null ? null : meta.name()))
                        .source(item.getSource() != null ? item.getSource()
                                : (meta == null ? null : meta.sourceFile()))
                        .reason(reason)
                        .build());
            }
        }
        return new DeliveredItems(items, excludedCount, excludedItems);
    }

    /**
     * 判断条目是否在本次下发前被剔除并给出原因。
     *
     * <p>当前读取侧只执行黑名单剔除（条目屏蔽 + 来源黑名单）；
     * 质量（quality）与策略（strategy）剔除在 408 召回阶段完成，本地快照不可见。
     *
     * @param item 结果集条目快照
     * @param blockedItemIds 已屏蔽条目 ID
     * @param blacklistedSources 黑名单来源名/数据集 ID
     * @return blacklist / quality / strategy；不剔除返回 null
     * @author cmr
     * @since 2026-11-11
     */
    private String excludedReasonOf(CmrResultItem item, Set<String> blockedItemIds,
                                    Set<String> blacklistedSources) {
        if (item.getItemId() != null && blockedItemIds.contains(item.getItemId())) {
            return EXCLUDE_REASON_BLACKLIST;
        }
        if (item.getSource() != null && blacklistedSources.contains(item.getSource())) {
            return EXCLUDE_REASON_BLACKLIST;
        }
        return null;
    }

    /** 下发结果与剔除明细的聚合（避免重复查询条目快照） */
    private record DeliveredItems(List<ResultItemVO> items, int excludedCount,
                                  List<ExcludedItemVO> excludedItems) {
    }

    /** 快照中的索引目录（兜底索引无 indexId，回看时按目录直查 meta.db）；缺失返回 null */
    private String snapshotIndexDir(CmrSearchHistory history) {
        if (history.getStrategySnapshotJson() == null || history.getStrategySnapshotJson().isBlank()) {
            return null;
        }
        try {
            JsonNode node = JsonUtils.readTree(history.getStrategySnapshotJson());
            JsonNode dir = node == null ? null : node.get("indexDir");
            return dir != null && dir.isTextual() && !dir.asText().isBlank() ? dir.asText() : null;
        } catch (Exception e) {
            log.debug("检索快照 indexDir 解析失败 searchId={} cause={}", history.getId(), e.getMessage(), e);
            return null;
        }
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
        CmrSearchHistory history = requireHistory(taskNo);
        BizException.assertTrue("T".equals(history.getSealed()), "任务已封存，赞/踩不可再改");
        Long userId = AuthContext.userId();
        BizException.assertNull(userId, "未登录");
        // SRH/TASK 双形态入参统一按 SRH 落库，避免同一任务产生两套赞踩记录
        String voteKey = history.getBizNo();

        com.hsmty.cmr.domain.search.entity.CmrItemVote existing = itemVoteMapper.selectOneByQuery(
                QueryWrapper.create().where(CMR_ITEM_VOTE.TASK_NO.eq(voteKey)
                        .and(CMR_ITEM_VOTE.ITEM_ID.eq(itemId))
                        .and(CMR_ITEM_VOTE.USER_ID.eq(userId))));
        String effectiveVote = vote;
        if ("none".equals(vote)) {
            if (existing != null) {
                itemVoteMapper.deleteById(existing.getId());
            }
            effectiveVote = null;
        } else if (existing == null) {
            // 取消后再投票：唯一键 uk_vote 不含 del_flag，逻辑删除的旧行必须复活而不是新插
            com.hsmty.cmr.domain.search.entity.CmrItemVote tombstone = findVoteIncludingDeleted(
                    voteKey, itemId, userId);
            if (tombstone != null) {
                reviveVote(tombstone.getId(), vote);
            } else {
                com.hsmty.cmr.domain.search.entity.CmrItemVote entity =
                        new com.hsmty.cmr.domain.search.entity.CmrItemVote();
                entity.setId(idWorker.next());
                entity.setTaskNo(voteKey);
                entity.setSearchId(history.getId());
                entity.setItemId(itemId);
                entity.setVote(vote);
                entity.setUserId(userId);
                entity.setDelFlag("F");
                entity.setCreateTime(LocalDateTime.now());
                itemVoteMapper.insert(entity, true);
            }
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

    /** 查含逻辑删除行的赞踩记录（绕过 del_flag='F' 过滤） */
    private com.hsmty.cmr.domain.search.entity.CmrItemVote findVoteIncludingDeleted(
            String taskNo, String itemId, Long userId) {
        return LogicDeleteManager.execWithoutLogicDelete(() -> itemVoteMapper.selectOneByQuery(
                QueryWrapper.create().where(CMR_ITEM_VOTE.TASK_NO.eq(taskNo)
                        .and(CMR_ITEM_VOTE.ITEM_ID.eq(itemId))
                        .and(CMR_ITEM_VOTE.USER_ID.eq(userId)))
                        .limit(1)));
    }

    /** 复活被逻辑删除的赞踩行：恢复 del_flag='F' 并写入新方向 */
    private void reviveVote(Long id, String vote) {
        LogicDeleteManager.execWithoutLogicDelete(() -> {
            UpdateChain.of(itemVoteMapper)
                    .set(CMR_ITEM_VOTE.VOTE, vote)
                    .set(CMR_ITEM_VOTE.DEL_FLAG, "F")
                    .where(CMR_ITEM_VOTE.ID.eq(id))
                    .update();
        });
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
        // 取消后再收藏：唯一键 uk_user_item 不含 del_flag，逻辑删除的旧行必须复活而不是新插
        com.hsmty.cmr.domain.search.entity.CmrFavorite tombstone =
                LogicDeleteManager.execWithoutLogicDelete(() -> favoriteMapper.selectOneByQuery(
                        QueryWrapper.create().where(CMR_FAVORITE.USER_ID.eq(userId)
                                .and(CMR_FAVORITE.ITEM_ID.eq(itemId)))
                                .limit(1)));
        if (tombstone != null) {
            LogicDeleteManager.execWithoutLogicDelete(() -> {
                UpdateChain.of(favoriteMapper)
                        .set(CMR_FAVORITE.ITEM_NAME, itemName)
                        .set(CMR_FAVORITE.SOURCE, source)
                        .set(CMR_FAVORITE.TASK_NO, displayFavoriteTaskNo(taskNo))
                        .set(CMR_FAVORITE.DEL_FLAG, "F")
                        .where(CMR_FAVORITE.ID.eq(tombstone.getId()))
                        .update();
            });
            return true;
        }
        com.hsmty.cmr.domain.search.entity.CmrFavorite entity =
                new com.hsmty.cmr.domain.search.entity.CmrFavorite();
        entity.setId(idWorker.next());
        entity.setUserId(userId);
        entity.setItemId(itemId);
        entity.setItemName(itemName);
        entity.setSource(source);
        // 对外统一展示 TASK 任务号；解析不到时原样保留
        entity.setTaskNo(displayFavoriteTaskNo(taskNo));
        entity.setDelFlag("F");
        entity.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(entity, true);
        return true;
    }

    /** 收藏记录展示用任务号：能解析到任务时统一 TASK 形态 */
    private String displayFavoriteTaskNo(String taskNo) {
        CmrSearchHistory history = findHistory(taskNo);
        return history == null ? taskNo : displayTaskNo(history);
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
        CmrSearchHistory history = requireHistory(taskNo);
        BizException.assertTrue("T".equals(history.getSealed()), "任务已封存");
        history.setSealed("T");
        historyMapper.update(history);
        log.info("检索任务封存: {}", taskNo);
    }

    /** 搜索结果摘要（P3）：Top-20 条目 + 赞/踩，30 天后过期 */
    public Map<String, Object> summary(String taskNo) {
        CmrSearchHistory history = requireHistory(taskNo);
        List<ResultItemVO> delivered = itemsOf(history);
        List<ResultItemVO> items = delivered.stream().limit(20).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        // 对外任务号统一为 TASK，SRH 另以 srhNo 下发给需要内部号的调用方
        result.put("taskNo", displayTaskNo(history));
        result.put("srhNo", history.getBizNo());
        result.put("searchId", history.getId());
        result.put("resultSetId", history.getResultSetId());
        result.put("taskStatus", taskService.statusOf(history.getTaskId()));
        result.put("errorMsg", taskService.errorMsgOf(history.getTaskId()));
        result.put("replayOfTaskNo", replayTaskNoOf(history.getReplayOfTaskNo()));
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

    /**
     * 搜索行为流水（P6）：任务级分页，含赞踩计数、封存状态与重放来源。
     *
     * @param query 分页参数（每页 10 条）
     * @param keyword 关键词：同时模糊匹配任务号 / 输入内容 / 操作人 / 重放来源任务号
     * @param operatorName 操作人（模糊）
     * @param zeroResult true 只看零结果
     * @param downCount true 只看踩数大于 0 的记录
     * @param taskNo 任务号（精确）
     * @param replayOfTaskNo 重放来源任务号（精确）
     * @param from 创建时间起
     * @param to 创建时间止
     * @return 行为流水分页
     * @author cmr
     * @since 2026-11-11
     */
    public PageResult<BehaviorVO> behaviors(PageQuery query, String keyword, String operatorName,
                                            Boolean zeroResult, Boolean downCount, String taskNo,
                                            String replayOfTaskNo, LocalDateTime from, LocalDateTime to) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_SEARCH_HISTORY);
        if (!blank(keyword)) {
            String like = "%" + keyword + "%";
            qw.where("(biz_no LIKE ? OR query_text LIKE ? OR operator_name LIKE ? OR replay_of_task_no LIKE ?"
                            + " OR task_id IN (SELECT id FROM cmr_task WHERE biz_no LIKE ?))",
                    like, like, like, like, like);
        }
        if (!blank(operatorName)) {
            qw.where(CMR_SEARCH_HISTORY.OPERATOR_NAME.like(operatorName));
        }
        if (Boolean.TRUE.equals(zeroResult)) {
            qw.where(CMR_SEARCH_HISTORY.ITEM_COUNT.eq(0));
        }
        if (downCount != null) {
            // true 只看有踩；false 只看无踩（与“全部”区分）
            qw.where((downCount ? "" : "NOT ") + "EXISTS (SELECT 1 FROM cmr_item_vote v"
                    + " WHERE v.search_id = cmr_search_history.id"
                    + " AND v.vote = 'down' AND v.del_flag = 'F')");
        }
        if (!blank(taskNo)) {
            // 兼容 SRH 检索号与 TASK 任务号两种过滤形态
            String no = taskNo.trim();
            qw.where("(biz_no = ? OR task_id IN (SELECT id FROM cmr_task WHERE biz_no = ?))", no, no);
        }
        if (!blank(replayOfTaskNo)) {
            // 历史表记录的是来源 SRH；入参为 TASK 时先换算为 SRH
            CmrSearchHistory source = findHistory(replayOfTaskNo);
            qw.where(CMR_SEARCH_HISTORY.REPLAY_OF_TASK_NO.eq(
                    source == null ? replayOfTaskNo.trim() : source.getBizNo()));
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
        List<BehaviorVO> records = page.getRecords().stream().map(h -> BehaviorVO.builder()
                .id(h.getId())
                .taskNo(displayTaskNo(h))
                .srhNo(h.getBizNo())
                .resultSetId(h.getResultSetId())
                .taskId(h.getTaskId())
                .taskBizNo(taskService.bizNoOf(h.getTaskId()))
                .taskStatus(taskService.statusOf(h.getTaskId()))
                .queryText(h.getQueryText())
                .queryFile(h.getQueryFile())
                .queryModality(h.getQueryModality())
                .targetModality(h.getTargetModality())
                .operatorName(h.getOperatorName())
                .strategyNo(h.getStrategyNo())
                .strategyVersion(h.getStrategyVersion())
                .threshold(h.getThreshold())
                .itemCount(h.getItemCount())
                .elapsedMs(h.getElapsedMs())
                .sealed("T".equals(h.getSealed()))
                .zeroResult(h.getItemCount() != null && h.getItemCount() == 0)
                .upCount(countVotes(h.getId(), "up"))
                .downCount(countVotes(h.getId(), "down"))
                .replayOfTaskNo(replayTaskNoOf(h.getReplayOfTaskNo()))
                .createTime(h.getCreateTime())
                .build()).toList();
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

    /**
     * 屏蔽条目详情：回查条目在检索快照中的模态、媒体路径与文本，供运营复核。
     *
     * <p>屏蔽记录只保存条目 ID 与来源，媒体路径不落库；这里按该条目最近出现的
     * 检索历史定位索引（cmr_index 或快照 indexDir），从 meta.db 还原元数据。
     * 索引已重建或条目已删除时元数据为空，调用方仍可展示屏蔽记录本身。</p>
     *
     * @param blockedId 屏蔽记录 ID
     * @return 屏蔽记录 + 可用的条目元数据
     * @author cmr
     * @since 2026-11-12
     */
    public Map<String, Object> blockedItemDetail(Long blockedId) {
        com.hsmty.cmr.domain.operation.entity.CmrBlockedItem entity =
                blockedItemMapper.selectOneById(blockedId);
        BizException.assertNull(entity, "屏蔽记录不存在");
        ItemMetaService.ItemMeta meta = resolveBlockedMeta(entity.getItemId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getId());
        item.put("itemId", entity.getItemId());
        item.put("itemName", entity.getItemName() != null ? entity.getItemName()
                : (meta == null ? null : meta.name()));
        item.put("source", entity.getSource() != null ? entity.getSource()
                : (meta == null ? null : meta.sourceFile()));
        item.put("reason", entity.getReason());
        item.put("feedbackId", entity.getFeedbackId());
        item.put("createTime", entity.getCreateTime());
        item.put("modality", meta == null ? null : meta.modality());
        item.put("sourceFile", meta == null ? null : meta.sourceFile());
        item.put("locatorJson", meta == null ? null : meta.locatorJson());
        item.put("text", meta == null ? null : meta.text());
        item.put("resolved", meta != null);
        return item;
    }

    /** 屏蔽条目元数据回查：按最近命中该条目的检索快照逐条尝试，命中即返回 */
    private ItemMetaService.ItemMeta resolveBlockedMeta(String itemId) {
        if (blank(itemId)) {
            return null;
        }
        List<CmrResultItem> rows = resultItemMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_RESULT_ITEM.ITEM_ID.eq(itemId))
                .orderBy(CMR_RESULT_ITEM.ID.desc())
                .limit(5));
        for (CmrResultItem row : rows) {
            CmrSearchHistory history = row.getSearchId() == null ? null
                    : historyMapper.selectOneById(row.getSearchId());
            if (history == null) {
                continue;
            }
            Map<String, ItemMetaService.ItemMeta> metas = history.getIndexId() != null
                    ? itemMetaService.lookup(history.getIndexId(), List.of(itemId))
                    : itemMetaService.lookupByDir(snapshotIndexDir(history), List.of(itemId));
            if (!metas.isEmpty()) {
                return metas.get(itemId);
            }
        }
        return null;
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

    /**
     * 按任务号解析检索历史（不存在时抛业务异常）。
     *
     * <p>原型与数据流以 TASK 任务号对外展示，历史表内部仍以 SRH 为业务号，
     * 因此两种形态都要接受：先按 SRH 精确匹配，未命中再按所属任务号反查。</p>
     *
     * @param taskNo SRH 检索号或 TASK 任务号
     * @return 检索历史
     * @author cmr
     * @since 2026-11-11
     */
    public CmrSearchHistory requireHistory(String taskNo) {
        CmrSearchHistory history = findHistory(taskNo);
        BizException.assertNull(history, "检索任务不存在: " + taskNo);
        return history;
    }

    /**
     * 按 SRH / TASK 双形态查找检索历史，找不到返回 null（不抛异常）。
     *
     * @param taskNo SRH 检索号或 TASK 任务号
     * @return 检索历史；不存在时返回 null
     * @author cmr
     * @since 2026-11-11
     */
    public CmrSearchHistory findHistory(String taskNo) {
        if (blank(taskNo)) {
            return null;
        }
        String no = taskNo.trim();
        CmrSearchHistory history = historyMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.BIZ_NO.eq(no)));
        if (history != null) {
            return history;
        }
        Long taskId = taskService.idOfByBizNo(no);
        if (taskId == null) {
            return null;
        }
        return historyMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.TASK_ID.eq(taskId)));
    }

    /**
     * 对外展示的任务号：优先 TASK 任务号，缺失时回落 SRH。
     *
     * @param history 检索历史
     * @return 任务号（TASK）
     * @author cmr
     * @since 2026-11-11
     */
    public String displayTaskNo(CmrSearchHistory history) {
        if (history == null) {
            return null;
        }
        String taskBizNo = taskService.bizNoOf(history.getTaskId());
        return blank(taskBizNo) ? history.getBizNo() : taskBizNo;
    }

    /** 重放来源任务号统一展示为 TASK（历史内部记录的是 SRH） */
    private String replayTaskNoOf(String replayOfSrhNo) {
        if (blank(replayOfSrhNo)) {
            return null;
        }
        CmrSearchHistory source = findHistory(replayOfSrhNo);
        return source == null ? replayOfSrhNo : displayTaskNo(source);
    }

    private String currentOperatorName() {
        com.hsmty.cmr.common.auth.LoginUser user = AuthContext.get();
        if (user == null) {
            return null;
        }
        return user.nickname() != null && !user.nickname().isBlank()
                ? user.nickname() : user.username();
    }

    /**
     * 归一化目标模态：为空/缺省返回 null，表示不限定目标模态（408 四模态召回）。
     *
     * @param targetModalities 前端传入的目标模态
     * @return 不可变列表；null 表示全部模态
     * @author cmr
     * @since 2026-11-11
     */
    private List<String> normalizedTargetModalities(List<String> targetModalities) {
        if (targetModalities == null || targetModalities.isEmpty()) {
            return null;
        }
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
