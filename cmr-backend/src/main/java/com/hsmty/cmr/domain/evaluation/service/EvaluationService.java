package com.hsmty.cmr.domain.evaluation.service;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvaluation;
import com.hsmty.cmr.domain.evaluation.mapper.EvaluationMapper;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.search.service.SearchService;
import com.hsmty.cmr.domain.search.vo.SearchResultVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hsmty.cmr.domain.evaluation.entity.table.CmrEvaluationTableDef.CMR_EVALUATION;
import static com.hsmty.cmr.domain.search.entity.table.CmrSearchHistoryTableDef.CMR_SEARCH_HISTORY;

/**
 * 效果评测服务（P8）：一条 query 一条评测，基准取历史检索，重放按 query 归集；
 * 退化判定：踩数上升 / 踩率 +5% / 结果数 −10% 任一命中即“退化”（数据流 3.6）
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

    private final EvaluationMapper evaluationMapper;
    private final SearchHistoryMapper historyMapper;
    private final com.hsmty.cmr.domain.search.mapper.ItemVoteMapper itemVoteMapper;
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

    /** 评测详情：基准 vs 最近一次重放，给出退化判定 */
    public Map<String, Object> detail(Long id) {
        CmrEvaluation evaluation = require(id);
        CmrSearchHistory base = historyMapper.selectOneById(evaluation.getBaseSearchId());
        List<CmrSearchHistory> replays = historyMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.EVAL_ID.eq(id))
                .orderBy(CMR_SEARCH_HISTORY.ID.desc()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", evaluation.getId());
        result.put("bizNo", evaluation.getBizNo());
        result.put("query", evaluation.getQuery());
        result.put("base", baseBrief(base, evaluation.getBaseDown()));
        result.put("replays", replays.stream().map(h -> baseBrief(h, downCount(h.getId()))).toList());
        if (replays.isEmpty()) {
            result.put("conclusion", "unevaluated");
            result.put("reasons", List.of());
            return result;
        }
        CmrSearchHistory latest = replays.get(0);
        result.put("latest", baseBrief(latest, downCount(latest.getId())));
        Map<String, Object> verdict = compare(evaluation.getBaseN(), evaluation.getBaseDown(),
                latest.getItemCount(), downCount(latest.getId()));
        result.put("conclusion", verdict.get("conclusion"));
        result.put("reasons", verdict.get("reasons"));
        result.put("downRateDelta", verdict.get("downRateDelta"));
        result.put("resultDropRatio", verdict.get("resultDropRatio"));
        return result;
    }

    /** 退化对比（也用于列表行内结论） */
    private Map<String, Object> compare(Integer baseN, Integer baseDown, Integer replayN, Integer replayDown) {
        int bn = baseN == null ? 0 : baseN;
        int bd = baseDown == null ? 0 : baseDown;
        int rn = replayN == null ? 0 : replayN;
        int rd = replayDown == null ? 0 : replayDown;
        double baseRate = bn == 0 ? 0 : (double) bd / bn;
        double replayRate = rn == 0 ? 0 : (double) rd / rn;
        double delta = replayRate - baseRate;
        double dropRatio = bn == 0 ? 0 : (double) (bn - rn) / bn;

        List<String> reasons = new java.util.ArrayList<>();
        if (rd > bd) {
            reasons.add("踩数上升（" + bd + " → " + rd + "）");
        }
        if (delta >= DOWN_RATE_DELTA) {
            reasons.add("踩率上升 " + Math.round(delta * 100) + "%");
        }
        if (dropRatio >= RESULT_DROP_RATIO) {
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
        row.put("baseTaskNo", e.getBaseTaskNo());
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
            row.put("latestTaskNo", latest.getBizNo());
            row.put("latestTime", latest.getCreateTime());
        }
        return row;
    }

    private Map<String, Object> baseBrief(CmrSearchHistory h, int down) {
        Map<String, Object> brief = new LinkedHashMap<>();
        if (h == null) {
            return brief;
        }
        brief.put("searchId", h.getId());
        brief.put("taskNo", h.getBizNo());
        brief.put("time", h.getCreateTime());
        brief.put("itemCount", h.getItemCount());
        brief.put("downCount", down);
        brief.put("strategyNo", h.getStrategyNo());
        brief.put("strategyVersion", h.getStrategyVersion());
        brief.put("threshold", h.getThreshold());
        return brief;
    }

    private int downCount(Long searchId) {
        Long count = itemVoteMapper.selectCountByQuery(QueryWrapper.create()
                .where(com.hsmty.cmr.domain.search.entity.table.CmrItemVoteTableDef.CMR_ITEM_VOTE
                        .SEARCH_ID.eq(searchId)
                        .and(com.hsmty.cmr.domain.search.entity.table.CmrItemVoteTableDef.CMR_ITEM_VOTE
                                .VOTE.eq("down"))));
        return count == null ? 0 : count.intValue();
    }

    private CmrEvaluation require(Long id) {
        CmrEvaluation evaluation = evaluationMapper.selectOneById(id);
        BizException.assertNull(evaluation, "评测不存在");
        return evaluation;
    }

    private CmrSearchHistory requireHistoryByTaskNo(String taskNo) {
        CmrSearchHistory history = historyMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_SEARCH_HISTORY.BIZ_NO.eq(taskNo)));
        BizException.assertNull(history, "基准检索任务不存在: " + taskNo);
        return history;
    }
}
