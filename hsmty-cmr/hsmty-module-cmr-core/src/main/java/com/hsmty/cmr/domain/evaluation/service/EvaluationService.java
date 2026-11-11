package com.hsmty.cmr.domain.evaluation.service;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvaluation;
import com.hsmty.cmr.domain.evaluation.mapper.EvaluationMapper;
import com.hsmty.cmr.domain.evaluation.mapper.HardCaseMapper;
import com.hsmty.cmr.domain.evaluation.vo.EvalMetricsVO;
import com.hsmty.cmr.domain.evaluation.vo.EvaluationCheckVO;
import com.hsmty.cmr.domain.evaluation.vo.EvaluationDetailVO;
import com.hsmty.cmr.domain.evaluation.vo.HardCaseVO;
import com.hsmty.cmr.domain.search.entity.CmrItemVote;
import com.hsmty.cmr.domain.search.entity.CmrResultItem;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.ItemVoteMapper;
import com.hsmty.cmr.domain.search.mapper.ResultItemMapper;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.search.service.SearchService;
import com.hsmty.cmr.domain.search.vo.SearchResultVO;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hsmty.cmr.domain.evaluation.entity.table.CmrEvaluationTableDef.CMR_EVALUATION;
import static com.hsmty.cmr.domain.search.entity.table.CmrItemVoteTableDef.CMR_ITEM_VOTE;
import static com.hsmty.cmr.domain.search.entity.table.CmrResultItemTableDef.CMR_RESULT_ITEM;
import static com.hsmty.cmr.domain.search.entity.table.CmrSearchHistoryTableDef.CMR_SEARCH_HISTORY;

/**
 * 效果评测服务（P8）：一条 query 一条评测，基准取历史检索，重放按 query 归集；
 * 退化判定：踩数上升 ≥ 1 / 踩率 +5 个百分点 / 结果数 −10% 任一命中即“退化”（数据流 3.6）。
 *
 * <p>基准与每次重放统一按 {@link EvalMetricsVO} 计算指标：结果数、踩数、踩率、Top-20 平均相似度、
 * Top-1 相似度、Top-20 与基准重叠率、平均相似度、耗时；指标缺失时返回 {@code null}。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final double DOWN_RATE_DELTA = 0.05;
    private static final double RESULT_DROP_RATIO = 0.10;
    private static final int DOWN_COUNT_DELTA = 1;
    private static final double EPSILON = 1e-12;
    private static final int TOP_N = 20;

    private final EvaluationMapper evaluationMapper;
    private final HardCaseMapper hardCaseMapper;
    private final SearchHistoryMapper historyMapper;
    private final ItemVoteMapper itemVoteMapper;
    private final ResultItemMapper resultItemMapper;
    private final SearchService searchService;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    public PageResult<Map<String, Object>> page(PageQuery query, String keyword) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_EVALUATION);
        if (keyword != null && !keyword.isBlank()) {
            qw.where(CMR_EVALUATION.QUERY.like(keyword));
        }
        qw.orderBy(CMR_EVALUATION.ID.desc());
        Page<CmrEvaluation> page = evaluationMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        List<Map<String, Object>> records = page.getRecords().stream().map(this::toRow).toList();
        return PageResult.of(records, page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /** 以历史检索为基准新建评测（同 query 唯一；已存在则返回原评测） */
    @Transactional(rollbackFor = Exception.class)
    public CmrEvaluation create(String baseTaskNo) {
        CmrSearchHistory base = requireHistoryByTaskNo(baseTaskNo);
        CmrEvaluation exists = evaluationMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_EVALUATION.QUERY.eq(base.getQueryText())));
        if (exists != null) {
            return exists;
        }
        CmrEvaluation evaluation = new CmrEvaluation();
        evaluation.setId(idWorker.next());
        evaluation.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_EVAL_RUN));
        evaluation.setQuery(base.getQueryText());
        evaluation.setBaseSearchId(base.getId());
        evaluation.setBaseTaskNo(base.getBizNo());
        evaluation.setBaseTime(base.getCreateTime());
        evaluation.setBaseN(base.getItemCount());
        evaluation.setBaseDown(downCount(base.getId()));
        evaluation.setDelFlag("F");
        evaluation.setCreateTime(LocalDateTime.now());
        evaluationMapper.insert(evaluation, true);
        log.info("评测登记: {} query={} base={}", evaluation.getBizNo(), evaluation.getQuery(), baseTaskNo);
        return evaluation;
    }

    /** 重放：以基准检索参数重跑，可指定待比较策略 */
    @Transactional(rollbackFor = Exception.class)
    public SearchResultVO replay(Long id, Long strategyId) {
        CmrEvaluation evaluation = require(id);
        SearchResultVO result = searchService.rerun(evaluation.getBaseSearchId(), strategyId, id);
        log.info("评测重放: {} strategy={} -> {}", evaluation.getBizNo(), strategyId, result.getSrhNo());
        return result;
    }

    /**
     * 评测详情：基准 vs 最近一次重放，给出退化判定、统一指标与 4 项只读数据检查。
     *
     * <p>指标字段沿用既有键（searchId/itemCount/downCount/strategyNo/strategyVersion/threshold），
     * 并追加 {@link EvalMetricsVO} 的契约字段；历史数据缺指标时对应值为 {@code null}，不抛异常。
     *
     * @param id 评测主键
     * @return 评测详情
     * @author cmr
     * @since 2026-11-11
     */
    public EvaluationDetailVO detail(Long id) {
        CmrEvaluation evaluation = require(id);
        CmrSearchHistory base = evaluation.getBaseSearchId() == null ? null
                : historyMapper.selectOneById(evaluation.getBaseSearchId());
        List<CmrSearchHistory> replays = historyMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.EVAL_ID.eq(id))
                .orderBy(CMR_SEARCH_HISTORY.ID.desc()));

        List<CmrResultItem> baseItems = resultItems(base);
        Set<String> baseTop20 = top20ItemIds(baseItems);
        // 基准踩数沿用登记时的快照（与退化判定口径一致）；快照缺失时回查当前赞踩
        int baseDown = evaluation.getBaseDown() != null ? evaluation.getBaseDown()
                : (base == null ? 0 : downCount(base.getId()));

        List<Map<String, Object>> replayRows = new ArrayList<>(replays.size());
        for (CmrSearchHistory replay : replays) {
            replayRows.add(baseBrief(replay, resultItems(replay), baseTop20, downCount(replay.getId())));
        }

        EvaluationDetailVO detail = EvaluationDetailVO.builder()
                .id(evaluation.getId())
                .bizNo(evaluation.getBizNo())
                .query(evaluation.getQuery())
                .base(baseBrief(base, baseItems, null, baseDown))
                .replays(replayRows)
                .conclusion("unevaluated")
                .reasons(List.of())
                .checks(checkData())
                .build();

        if (replays.isEmpty()) {
            return detail;
        }
        CmrSearchHistory latest = replays.get(0);
        Map<String, Object> verdict = compare(evaluation.getBaseN(), evaluation.getBaseDown(),
                latest.getItemCount(), downCount(latest.getId()));
        detail.setLatest(replayRows.get(0));
        detail.setConclusion(String.valueOf(verdict.get("conclusion")));
        detail.setReasons(reasonsOf(verdict));
        detail.setDownRateDelta(numberOf(verdict.get("downRateDelta")));
        detail.setResultDropRatio(numberOf(verdict.get("resultDropRatio")));
        return detail;
    }

    /**
     * 难例样本集：有点踩的检索任务，按被踩条目数倒序分页，同时用于「新建评测」候选记录。
     *
     * <p>被踩条目数按 {@code itemId} 去重，不统计点踩次数。
     *
     * @param query 分页参数
     * @return 难例样本分页
     * @author cmr
     * @since 2026-11-11
     */
    public PageResult<HardCaseVO> hardCases(PageQuery query) {
        int pageNum = Math.max(query.getPageNum(), 1);
        int pageSize = Math.max(query.getPageSize(), 1);
        long total = hardCaseMapper.countDownSearches();
        List<HardCaseVO> records = total == 0 ? List.of()
                : hardCaseMapper.selectHardCases((pageNum - 1) * pageSize, pageSize);
        return PageResult.of(records, total, pageNum, pageSize);
    }

    /** 退化对比（也用于列表行内结论）：边界按原型取 >= / <= */
    private Map<String, Object> compare(Integer baseN, Integer baseDown, Integer replayN, Integer replayDown) {
        int bn = baseN == null ? 0 : baseN;
        int bd = baseDown == null ? 0 : baseDown;
        int rn = replayN == null ? 0 : replayN;
        int rd = replayDown == null ? 0 : replayDown;
        double baseRate = bn == 0 ? 0 : (double) bd / bn;
        double replayRate = rn == 0 ? 0 : (double) rd / rn;
        double delta = replayRate - baseRate;
        double dropRatio = bn == 0 ? 0 : (double) (bn - rn) / bn;

        List<String> reasons = new ArrayList<>();
        // 踩数上升 >= 1
        if (rd - bd >= DOWN_COUNT_DELTA) {
            reasons.add("踩数上升（" + bd + " → " + rd + "）");
        }
        // 踩率上升 >= 5 个百分点
        if (delta >= DOWN_RATE_DELTA - EPSILON) {
            reasons.add("踩率上升 " + Math.round(delta * 100) + "%");
        }
        // 结果数下降 >= 10%（等价于 重放/基准 <= 0.90）
        if (dropRatio >= RESULT_DROP_RATIO - EPSILON) {
            reasons.add("结果数下降 " + Math.round(dropRatio * 100) + "%");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conclusion", reasons.isEmpty() ? "normal" : "degraded");
        result.put("reasons", reasons);
        result.put("downRateDelta", Math.round(delta * 1000) / 10.0);
        result.put("resultDropRatio", Math.round(dropRatio * 1000) / 10.0);
        return result;
    }

    private Map<String, Object> toRow(CmrEvaluation e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("bizNo", e.getBizNo());
        row.put("query", e.getQuery());
        // 对外任务号统一为 TASK；历史缺失时回落登记时保存的 SRH
        CmrSearchHistory baseHistory = searchService.findHistory(e.getBaseTaskNo());
        row.put("baseTaskNo", baseHistory == null ? e.getBaseTaskNo() : searchService.displayTaskNo(baseHistory));
        row.put("baseTime", e.getBaseTime());
        row.put("baseN", e.getBaseN());
        row.put("baseDown", e.getBaseDown());
        List<CmrSearchHistory> replays = historyMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.EVAL_ID.eq(e.getId()))
                .orderBy(CMR_SEARCH_HISTORY.ID.desc()));
        row.put("replayCount", replays.size());
        if (replays.isEmpty()) {
            row.put("conclusion", "unevaluated");
            row.put("latestTaskNo", null);
            row.put("latestTime", null);
        } else {
            CmrSearchHistory latest = replays.get(0);
            Map<String, Object> verdict = compare(e.getBaseN(), e.getBaseDown(),
                    latest.getItemCount(), downCount(latest.getId()));
            row.put("conclusion", verdict.get("conclusion"));
            row.put("reasons", verdict.get("reasons"));
            row.put("latestTaskNo", searchService.displayTaskNo(latest));
            row.put("latestTime", latest.getCreateTime());
        }
        return row;
    }

    /**
     * 单次检索指标视图：保留既有字段并追加 EvalMetrics 契约字段。
     *
     * @param h             检索历史
     * @param items         结果条目（按 rank 升序）
     * @param referenceTop20 基准 Top-20 itemId 集合；传 null 表示与自身比较
     * @param down          踩数
     * @return 指标键值（检索历史缺失时返回空 map）
     */
    private Map<String, Object> baseBrief(CmrSearchHistory h, List<CmrResultItem> items,
                                          Set<String> referenceTop20, int down) {
        Map<String, Object> brief = new LinkedHashMap<>();
        if (h == null) {
            return brief;
        }
        EvalMetricsVO metrics = computeMetrics(h, items, referenceTop20, down);
        brief.put("searchId", h.getId());
        brief.put("taskNo", searchService.displayTaskNo(h));
        brief.put("time", h.getCreateTime());
        brief.put("itemCount", h.getItemCount());
        brief.put("downCount", down);
        brief.put("strategyNo", h.getStrategyNo());
        brief.put("strategyVersion", h.getStrategyVersion());
        brief.put("threshold", h.getThreshold());
        brief.put("items", metrics.getItems());
        brief.put("down", metrics.getDown());
        brief.put("downRate", metrics.getDownRate());
        brief.put("top20AvgSim", metrics.getTop20AvgSim());
        brief.put("top1Sim", metrics.getTop1Sim());
        brief.put("top20Overlap", metrics.getTop20Overlap());
        brief.put("avgSim", metrics.getAvgSim());
        brief.put("elapsedMs", metrics.getElapsedMs());
        return brief;
    }

    /** 按结果条目与赞踩计算单次运行指标；相似度缺失时对应字段为 null */
    private EvalMetricsVO computeMetrics(CmrSearchHistory history, List<CmrResultItem> items,
                                         Set<String> referenceTop20, int down) {
        List<CmrResultItem> top = items.size() > TOP_N ? items.subList(0, TOP_N) : items;
        List<Double> topSims = new ArrayList<>(top.size());
        List<Double> allSims = new ArrayList<>(items.size());
        for (CmrResultItem item : items) {
            Double sim = similarityOf(item);
            if (sim != null) {
                allSims.add(sim);
            }
        }
        for (CmrResultItem item : top) {
            Double sim = similarityOf(item);
            if (sim != null) {
                topSims.add(sim);
            }
        }
        Set<String> currentTop20 = top20ItemIds(top);
        // referenceTop20 为 null 表示基准自身（自比重叠率）；非 null 但为空表示基准无条目，重叠率不可知
        Set<String> reference = referenceTop20 == null ? currentTop20 : referenceTop20;

        Integer itemCount = items.isEmpty() ? history.getItemCount() : items.size();
        Double downRate = itemCount != null && itemCount > 0 ? round4((double) down / itemCount) : null;
        Double top20AvgSim = topSims.isEmpty() ? null
                : round4(topSims.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        Double top1Sim = topSims.isEmpty() ? null
                : round4(topSims.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        Double avgSim = allSims.isEmpty() ? null
                : round4(allSims.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        Double top20Overlap = reference.isEmpty() ? null
                : round4((double) currentTop20.stream().filter(reference::contains).count() / TOP_N);

        return EvalMetricsVO.builder()
                .taskNo(searchService.displayTaskNo(history))
                .time(history.getCreateTime())
                .items(itemCount)
                .down(down)
                .downRate(downRate)
                .top20AvgSim(top20AvgSim)
                .top1Sim(top1Sim)
                .top20Overlap(top20Overlap)
                .avgSim(avgSim)
                .elapsedMs(history.getElapsedMs())
                .build();
    }

    /** 取检索结果条目（按 rank 升序）；检索历史缺失时返回空集合 */
    private List<CmrResultItem> resultItems(CmrSearchHistory history) {
        if (history == null || history.getId() == null) {
            return List.of();
        }
        return resultItemMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_RESULT_ITEM.SEARCH_ID.eq(history.getId()))
                .orderBy(CMR_RESULT_ITEM.RANK.asc()));
    }

    /** 按 rank 顺序取前 20 条的非空 itemId */
    private Set<String> top20ItemIds(List<CmrResultItem> items) {
        Set<String> ids = new LinkedHashSet<>();
        int limit = Math.min(items.size(), TOP_N);
        for (int i = 0; i < limit; i++) {
            String itemId = items.get(i).getItemId();
            if (itemId != null && !itemId.isBlank()) {
                ids.add(itemId);
            }
        }
        return ids;
    }

    /** 相似度优先取 score，历史数据缺失时回落对齐分 */
    private Double similarityOf(CmrResultItem item) {
        BigDecimal score = item.getScore() != null ? item.getScore() : item.getAlignScore();
        return score == null ? null : score.doubleValue();
    }

    private Double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /**
     * 检查评测数据 4 项（只读、全库口径）：样本够不够、有没有重复用、样本还有效吗、评价矛盾吗。
     *
     * <p>对应 P8「检查评测数据」按钮；仅统计逻辑未删除数据，不改动任何记录。
     *
     * @return 4 项检查结果
     * @author cmr
     * @since 2026-11-11
     */
    public List<EvaluationCheckVO> checkData() {
        long evaluations = countOf(evaluationMapper.countEvaluations());
        long baseItems = countOf(evaluationMapper.countBaseItems());
        long reused = countOf(evaluationMapper.countReusedItems());
        long invalid = countOf(evaluationMapper.countInvalidItems());
        long contradictory = countOf(evaluationMapper.countContradictoryItems());

        List<EvaluationCheckVO> checks = new ArrayList<>(4);
        checks.add(EvaluationCheckVO.builder()
                .key("sample_enough")
                .label("样本够不够")
                .ok(evaluations > 0 && baseItems > 0)
                .detail("共 " + evaluations + " 个评测、基准样本 " + baseItems + " 条"
                        + (evaluations > 0 && baseItems > 0 ? "" : "，样本不足无法评测"))
                .build());
        checks.add(EvaluationCheckVO.builder()
                .key("sample_reuse")
                .label("有没有重复用")
                .ok(reused == 0)
                .detail(reused == 0 ? "样本未在多个评测基准中重复出现"
                        : "有 " + reused + " 条样本出现在多个评测基准中")
                .build());
        checks.add(EvaluationCheckVO.builder()
                .key("sample_valid")
                .label("样本还有效吗")
                .ok(invalid == 0)
                .detail(invalid == 0 ? "基准样本均未被屏蔽、来源均未进黑名单"
                        : "有 " + invalid + " 条基准样本已被屏蔽或来源已进黑名单")
                .build());
        checks.add(EvaluationCheckVO.builder()
                .key("vote_contradiction")
                .label("评价矛盾吗")
                .ok(contradictory == 0)
                .detail(contradictory == 0 ? "未发现同一结果在不同任务中既超有用又超无用"
                        : "有 " + contradictory + " 条结果在不同任务中评价相反")
                .build());
        return checks;
    }

    private long countOf(Long value) {
        return value == null ? 0L : value;
    }

    @SuppressWarnings("unchecked")
    private List<String> reasonsOf(Map<String, Object> verdict) {
        Object reasons = verdict.get("reasons");
        return reasons instanceof List<?> list ? (List<String>) list : List.of();
    }

    private Double numberOf(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private int downCount(Long searchId) {
        Long count = itemVoteMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_ITEM_VOTE.SEARCH_ID.eq(searchId)
                        .and(CMR_ITEM_VOTE.VOTE.eq(CmrItemVote.VOTE_DOWN))));
        return count == null ? 0 : count.intValue();
    }

    private CmrEvaluation require(Long id) {
        CmrEvaluation evaluation = evaluationMapper.selectOneById(id);
        BizException.assertNull(evaluation, "评测不存在");
        return evaluation;
    }

    private CmrSearchHistory requireHistoryByTaskNo(String taskNo) {
        // 兼容 SRH 与 TASK 两种任务号形态（P7 难例表对外展示 TASK）
        CmrSearchHistory history = searchService.findHistory(taskNo);
        BizException.assertNull(history, "基准检索任务不存在: " + taskNo);
        return history;
    }
}
