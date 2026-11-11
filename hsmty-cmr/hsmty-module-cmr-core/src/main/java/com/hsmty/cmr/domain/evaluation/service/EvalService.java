package com.hsmty.cmr.domain.evaluation.service;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.evaluation.dto.EvalRunDTO;
import com.hsmty.cmr.domain.evaluation.dto.EvalSetCreateDTO;
import com.hsmty.cmr.domain.evaluation.dto.LabelDTO;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvalItem;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvalRecord;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvalSet;
import com.hsmty.cmr.domain.evaluation.mapper.EvalItemMapper;
import com.hsmty.cmr.domain.evaluation.mapper.EvalRecordMapper;
import com.hsmty.cmr.domain.evaluation.mapper.EvalSetMapper;
import com.hsmty.cmr.domain.evaluation.vo.EvalItemVO;
import com.hsmty.cmr.domain.evaluation.vo.EvalPreviewVO;
import com.hsmty.cmr.domain.evaluation.vo.EvalRecordVO;
import com.hsmty.cmr.domain.evaluation.vo.EvalSetVO;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.strategy.entity.CmrStrategyVersion;
import com.hsmty.cmr.domain.strategy.mapper.StrategyVersionMapper;
import com.hsmty.cmr.domain.task.TaskExecutor;
import com.hsmty.cmr.domain.task.TaskService;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.domain.task.vo.TaskVO;
import com.hsmty.cmr.file.FileService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrIndexTableDef.CMR_INDEX;
import static com.hsmty.cmr.domain.evaluation.entity.table.CmrEvalItemTableDef.CMR_EVAL_ITEM;
import static com.hsmty.cmr.domain.evaluation.entity.table.CmrEvalRecordTableDef.CMR_EVAL_RECORD;
import static com.hsmty.cmr.domain.evaluation.entity.table.CmrEvalSetTableDef.CMR_EVAL_SET;
import static com.hsmty.cmr.domain.strategy.entity.table.CmrStrategyVersionTableDef.CMR_STRATEGY_VERSION;

/**
 * 评测服务：评测集创建（三类筛选）、人工标注、完成标注、评测执行（409 异步）、历史与对比
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int FILTER_LIMIT = 200;

    private final EvalSetMapper evalSetMapper;
    private final EvalItemMapper evalItemMapper;
    private final EvalRecordMapper evalRecordMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final IndexMapper indexMapper;
    private final StrategyVersionMapper strategyVersionMapper;
    private final FileService fileService;
    private final TaskService taskService;
    private final TaskExecutor taskExecutor;
    private final EvalPipeline evalPipeline;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    public PageResult<EvalSetVO> page(PageQuery query, String name, String status) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_EVAL_SET);
        if (notBlank(name)) {
            qw.where(CMR_EVAL_SET.NAME.like(name));
        }
        if (notBlank(status)) {
            qw.where(CMR_EVAL_SET.STATUS.eq(status));
        }
        qw.orderBy(CMR_EVAL_SET.ID.desc());
        Page<CmrEvalSet> page = evalSetMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(),
                page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /** 创建评测集：按筛选条件从检索历史抽取条目，状态=标注中 */
    @Transactional(rollbackFor = Exception.class)
    public EvalSetVO create(EvalSetCreateDTO dto) {
        Map<String, Boolean> filters = dto.getFilters() == null ? Map.of() : dto.getFilters();
        boolean zeroResult = Boolean.TRUE.equals(filters.get("zeroResult"));
        boolean noClick = Boolean.TRUE.equals(filters.get("noClick"));
        boolean reSearch = Boolean.TRUE.equals(filters.get("reSearch"));
        BizException.assertTrue(!zeroResult && !noClick && !reSearch, "至少选择一种筛选条件");

        Map<Long, CmrSearchHistory> picked = pickHistories(zeroResult, noClick, reSearch);
        BizException.assertTrue(picked.isEmpty(), "筛选结果为空，无法创建评测集");

        CmrEvalSet set = new CmrEvalSet();
        set.setId(idWorker.next());
        set.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_EVAL_SET));
        set.setName(dto.getName());
        set.setFiltersJson(JsonUtils.write(filters));
        set.setStatus(CmrEvalSet.STATUS_LABELING);
        set.setQueryCount(picked.size());
        set.setDelFlag("F");
        set.setCreateTime(LocalDateTime.now());
        evalSetMapper.insert(set, true);

        for (CmrSearchHistory h : picked.values()) {
            CmrEvalItem item = new CmrEvalItem();
            item.setId(idWorker.next());
            item.setEvalSetId(set.getId());
            item.setSearchId(h.getId());
            item.setQuery(h.getQueryText() == null ? "(文件检索) " + h.getQueryFile() : h.getQueryText());
            item.setLabeled(false);
            item.setDelFlag("F");
            item.setCreateTime(LocalDateTime.now());
            evalItemMapper.insert(item, true);
        }
        log.info("评测集创建: {} 条目 {} 条（筛选 {}）", set.getBizNo(), picked.size(), filters.keySet());
        return toVO(set);
    }

    /** 创建前预览：复用创建筛选口径，返回命中总数与前 10 条样例 */
    public EvalPreviewVO preview(boolean zeroResult, boolean noClick, boolean reSearch) {
        BizException.assertTrue(!zeroResult && !noClick && !reSearch, "至少选择一种筛选条件");
        Map<Long, CmrSearchHistory> picked = pickHistories(zeroResult, noClick, reSearch);
        List<EvalPreviewVO.Item> items = picked.values().stream()
                .limit(10)
                .map(h -> EvalPreviewVO.Item.builder()
                        .searchId(h.getId()).searchBizNo(h.getBizNo())
                        .query(h.getQueryText() == null ? "(文件检索) " + h.getQueryFile() : h.getQueryText())
                        .queryModality(h.getQueryModality()).targetModality(h.getTargetModality())
                        .itemCount(h.getItemCount()).createTime(h.getCreateTime())
                        .build())
                .toList();
        return EvalPreviewVO.builder().count((long) picked.size()).items(items).build();
    }

    private Map<Long, CmrSearchHistory> pickHistories(boolean zeroResult, boolean noClick, boolean reSearch) {
        Map<Long, CmrSearchHistory> picked = new LinkedHashMap<>();
        if (zeroResult) {
            searchHistoryMapper.selectZeroResult(FILTER_LIMIT).forEach(h -> picked.put(h.getId(), h));
        }
        if (noClick) {
            searchHistoryMapper.selectNoClick(FILTER_LIMIT).forEach(h -> picked.putIfAbsent(h.getId(), h));
        }
        if (reSearch) {
            searchHistoryMapper.selectReSearch(FILTER_LIMIT).forEach(h -> picked.putIfAbsent(h.getId(), h));
        }
        return picked;
    }

    public CmrEvalSet requireSet(Long id) {
        CmrEvalSet set = evalSetMapper.selectOneById(id);
        BizException.assertNull(set, "评测集不存在");
        return set;
    }

    /** 详情 + 条目分页 */
    public Map<String, Object> detail(Long id, PageQuery query) {
        CmrEvalSet set = requireSet(id);
        Page<CmrEvalItem> page = evalItemMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1),
                QueryWrapper.create().where(CMR_EVAL_ITEM.EVAL_SET_ID.eq(id)).orderBy(CMR_EVAL_ITEM.ID.asc()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evalSet", toVO(set));
        result.put("items", PageResult.of(page.getRecords().stream().map(EvalItemVO::from).toList(),
                page.getTotalRow(), query.getPageNum(), query.getPageSize()));
        return result;
    }

    /** 标注：写标准答案 */
    public void label(Long setId, Long itemId, List<String> answers) {
        CmrEvalItem item = evalItemMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_EVAL_ITEM.ID.eq(itemId).and(CMR_EVAL_ITEM.EVAL_SET_ID.eq(setId))));
        BizException.assertNull(item, "评测条目不存在");
        item.setAnswersJson(JsonUtils.write(answers));
        item.setLabeled(true);
        evalItemMapper.update(item);
    }

    /** 完成标注：每条已标才允许；labeling→done（此后才可执行评测） */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        CmrEvalSet set = requireSet(id);
        BizException.assertTrue(!CmrEvalSet.STATUS_LABELING.equals(set.getStatus()),
                "仅标注中的评测集可完成标注");
        Long unlabeled = evalItemMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_EVAL_ITEM.EVAL_SET_ID.eq(id).and(CMR_EVAL_ITEM.LABELED.eq(false))));
        BizException.assertTrue(unlabeled != null && unlabeled > 0, "仍有 " + unlabeled + " 条未标注");
        set.setStatus(CmrEvalSet.STATUS_DONE);
        evalSetMapper.update(set);
    }

    /** 执行评测：写评测文件 → EVAL 记录 + TASK(type=eval) 异步跑 409 */
    @Transactional(rollbackFor = Exception.class)
    public EvalRecordVO run(EvalRunDTO dto) {
        CmrEvalSet set = requireSet(dto.getEvalSetId());
        BizException.assertTrue(!CmrEvalSet.STATUS_DONE.equals(set.getStatus()),
                "评测集未完成标注，不可执行评测");

        List<CmrEvalItem> items = evalItemMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_EVAL_ITEM.EVAL_SET_ID.eq(set.getId())));
        List<String> lines = new ArrayList<>();
        for (CmrEvalItem item : items) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("query", item.getQuery());
            line.put("answers", item.getAnswersJson() == null ? List.of()
                    : JsonUtils.read(item.getAnswersJson(),
                            new tools.jackson.core.type.TypeReference<List<String>>() {
                            }));
            lines.add(JsonUtils.write(line));
        }
        String evalFile = fileService.writeRuntimeText(
                "eval/" + set.getBizNo() + "_" + LocalDateTime.now().format(TS) + ".jsonl",
                String.join("\n", lines) + "\n");

        CmrIndex index = indexMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_INDEX.STATUS.eq(CmrIndex.STATUS_ACTIVE))
                .orderBy(CMR_INDEX.ACTIVATED_TIME.desc())
                .limit(1));
        BizException.assertNull(index, "暂无激活索引，无法评测");

        CmrEvalRecord record = new CmrEvalRecord();
        record.setId(idWorker.next());
        record.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_EVAL_RUN));
        record.setEvalSetId(set.getId());
        record.setIndexId(index.getId());
        record.setStrategyVersionId(dto.getTargetStrategyVersionId());
        record.setDelFlag("F");
        record.setCreateTime(LocalDateTime.now());
        evalRecordMapper.insert(record, true);

        Long recordId = record.getId();
        String indexDir = index.getIndexDir();
        Long targetVersionId = dto.getTargetStrategyVersionId();
        CmrTask task = taskService.create(CmrTask.TYPE_EVAL, recordId,
                Map.of("evalRecordId", recordId, "evalFile", evalFile, "indexDir", indexDir));
        record.setTaskId(task.getId());
        evalRecordMapper.update(record);
        taskExecutor.submit(task, t -> evalPipeline.run(t, recordId, evalFile, indexDir, targetVersionId));
        log.info("评测任务已提交: {} record={} target={}", task.getBizNo(), record.getBizNo(), targetVersionId);
        return toRecordVO(record);
    }

    public PageResult<EvalRecordVO> records(PageQuery query, Long evalSetId) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_EVAL_RECORD);
        if (evalSetId != null) {
            qw.where(CMR_EVAL_RECORD.EVAL_SET_ID.eq(evalSetId));
        }
        qw.orderBy(CMR_EVAL_RECORD.ID.desc());
        Page<CmrEvalRecord> page = evalRecordMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        return PageResult.of(page.getRecords().stream().map(this::toRecordVO).toList(),
                page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    public EvalRecordVO recordDetail(Long id) {
        CmrEvalRecord record = evalRecordMapper.selectOneById(id);
        BizException.assertNull(record, "评测记录不存在");
        return toRecordVO(record);
    }

    private EvalSetVO toVO(CmrEvalSet e) {
        Long labeled = evalItemMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_EVAL_ITEM.EVAL_SET_ID.eq(e.getId()).and(CMR_EVAL_ITEM.LABELED.eq(true))));
        return EvalSetVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).name(e.getName())
                .filtersJson(e.getFiltersJson()).status(e.getStatus())
                .queryCount(e.getQueryCount()).labeledCount(labeled == null ? 0 : labeled)
                .createTime(e.getCreateTime())
                .build();
    }

    private EvalRecordVO toRecordVO(CmrEvalRecord e) {
        return EvalRecordVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).evalSetId(e.getEvalSetId())
                .indexId(e.getIndexId()).strategyVersionId(e.getStrategyVersionId())
                .taskId(e.getTaskId()).taskBizNo(taskService.bizNoOf(e.getTaskId()))
                .taskStatus(taskService.statusOf(e.getTaskId()))
                .metricsJson(e.getMetricsJson()).linesJson(e.getLinesJson())
                .conclusion(e.getConclusion()).failedLinesJson(e.getFailedLinesJson())
                .comparisonJson(e.getComparisonJson()).createTime(e.getCreateTime())
                .build();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
