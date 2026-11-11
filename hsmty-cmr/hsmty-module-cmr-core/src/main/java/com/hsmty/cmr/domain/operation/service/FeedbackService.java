package com.hsmty.cmr.domain.operation.service;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.auth.AuthContext;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.operation.dto.FeedbackCreateDTO;
import com.hsmty.cmr.domain.operation.dto.FeedbackHandleDTO;
import com.hsmty.cmr.domain.operation.entity.CmrFeedback;
import com.hsmty.cmr.domain.operation.mapper.FeedbackMapper;
import com.hsmty.cmr.domain.operation.vo.FeedbackStatsVO;
import com.hsmty.cmr.domain.operation.vo.FeedbackVO;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.search.service.SearchService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hsmty.cmr.domain.operation.entity.table.CmrFeedbackTableDef.CMR_FEEDBACK;

/**
 * 反馈单服务（P7）：两个入口（标记不匹配 / 零结果分析）→ 一个判断 → 三个去向（数据流 3.7）
 * <p>
 * 处置阶段按入口类型强校验「判断 → 去向」固定映射，非法组合直接抛业务异常。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final SearchService searchService;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    @Transactional(rollbackFor = Exception.class)
    public FeedbackVO create(FeedbackCreateDTO dto) {
        BizException.assertTrue(!CmrFeedback.ENTRY_MISMATCH.equals(dto.getEntryType())
                && !CmrFeedback.ENTRY_ZERO.equals(dto.getEntryType()), "非法反馈入口: " + dto.getEntryType());
        CmrSearchHistory history = null;
        String sourceTaskNo = dto.getSourceTaskNo();
        if (notBlank(sourceTaskNo)) {
            // 兼容 SRH/TASK 两种形态，登记时统一存 TASK 任务号
            history = searchService.findHistory(sourceTaskNo);
            if (history != null) {
                sourceTaskNo = searchService.displayTaskNo(history);
            }
        }
        BizException.assertTrue(CmrFeedback.ENTRY_MISMATCH.equals(dto.getEntryType()) && history == null,
                "标记不匹配必须指定有效的来源任务");

        // 同一任务重复标记不匹配：返回已有待处理反馈单，避免运营重复处置
        if (CmrFeedback.ENTRY_MISMATCH.equals(dto.getEntryType())) {
            CmrFeedback pending = feedbackMapper.selectOneByQuery(QueryWrapper.create()
                    .where(CMR_FEEDBACK.SOURCE_TASK_NO.eq(sourceTaskNo)
                            .and(CMR_FEEDBACK.ENTRY_TYPE.eq(CmrFeedback.ENTRY_MISMATCH))
                            .and(CMR_FEEDBACK.STATUS.eq(CmrFeedback.STATUS_PENDING)))
                    .orderBy(CMR_FEEDBACK.ID.desc())
                    .limit(1));
            if (pending != null) {
                // 同一任务重复标记：把本次新勾选的条目合并进待处理单，避免用户选择被静默丢弃
                List<FeedbackCreateDTO.MismatchItem> merged = mergeItems(
                        pending.getMismatchItemsJson(), dto.getItems());
                pending.setMismatchItemsJson(merged.isEmpty() ? null : JsonUtils.write(merged));
                pending.setUpdateTime(LocalDateTime.now());
                feedbackMapper.update(pending);
                log.info("反馈重复提交，已合并条目到待处理单: {} task={} items={}",
                        pending.getBizNo(), sourceTaskNo, merged.size());
                return FeedbackVO.from(pending);
            }
        }

        CmrFeedback feedback = new CmrFeedback();
        feedback.setId(idWorker.next());
        feedback.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_FEEDBACK));
        feedback.setSearchId(history == null ? null : history.getId());
        feedback.setSourceTaskNo(sourceTaskNo);
        feedback.setSourceQuery(dto.getSourceQuery() != null ? dto.getSourceQuery()
                : (history == null ? null : history.getQueryText()));
        feedback.setEntryType(dto.getEntryType());
        feedback.setType(dto.getEntryType());
        feedback.setMismatchItemsJson(dto.getItems() == null || dto.getItems().isEmpty()
                ? null : JsonUtils.write(dto.getItems()));
        feedback.setDescription(dto.getDescription());
        feedback.setStatus(CmrFeedback.STATUS_PENDING);
        feedback.setBlocked(false);
        feedback.setDelFlag("F");
        feedback.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(feedback, true);
        log.info("反馈登记: {} entry={} task={}", feedback.getBizNo(), dto.getEntryType(), sourceTaskNo);
        return FeedbackVO.from(feedback);
    }

    public FeedbackStatsVO stats() {
        return FeedbackStatsVO.builder()
                .total(count(null))
                .pending(count(CmrFeedback.STATUS_PENDING))
                .handled(count(CmrFeedback.STATUS_HANDLED))
                .blocked(countDisposition(CmrFeedback.DISPOSITION_BLOCK))
                .strategy(countDisposition(CmrFeedback.DISPOSITION_STRATEGY))
                .supplement(countDisposition(CmrFeedback.DISPOSITION_SUPPLEMENT))
                .build();
    }

    /** 合并待处理单已有条目与本次新勾选条目（按 itemId 去重，保持原顺序） */
    private List<FeedbackCreateDTO.MismatchItem> mergeItems(
            String existingJson, List<FeedbackCreateDTO.MismatchItem> incoming) {
        Map<String, FeedbackCreateDTO.MismatchItem> merged = new LinkedHashMap<>();
        if (existingJson != null && !existingJson.isBlank()) {
            List<FeedbackCreateDTO.MismatchItem> existing = JsonUtils.read(existingJson,
                    new TypeReference<List<FeedbackCreateDTO.MismatchItem>>() {
                    });
            if (existing != null) {
                existing.forEach(item -> {
                    if (item != null && item.getItemId() != null) {
                        merged.put(item.getItemId(), item);
                    }
                });
            }
        }
        if (incoming != null) {
            incoming.stream().filter(item -> item != null && item.getItemId() != null)
                    .forEach(item -> merged.put(item.getItemId(), item));
        }
        return new ArrayList<>(merged.values());
    }

    private Long count(String status) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_FEEDBACK);
        if (status != null) {
            qw.where(CMR_FEEDBACK.STATUS.eq(status));
        }
        Long count = feedbackMapper.selectCountByQuery(qw);
        return count == null ? 0 : count;
    }

    private Long countDisposition(String disposition) {
        Long count = feedbackMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_FEEDBACK.DISPOSITION.eq(disposition)));
        return count == null ? 0 : count;
    }

    public PageResult<FeedbackVO> page(PageQuery query, String entryType, String status, String sourceTaskNo) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_FEEDBACK);
        if (notBlank(sourceTaskNo)) {
            qw.where(CMR_FEEDBACK.SOURCE_TASK_NO.eq(sourceTaskNo));
        }
        if (notBlank(entryType)) {
            qw.where(CMR_FEEDBACK.ENTRY_TYPE.eq(entryType));
        }
        if (notBlank(status)) {
            qw.where(CMR_FEEDBACK.STATUS.eq(status));
        }
        qw.orderBy(CMR_FEEDBACK.ID.desc());
        Page<CmrFeedback> page = feedbackMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        List<FeedbackVO> vos = page.getRecords().stream().map(FeedbackVO::from).toList();
        return PageResult.of(vos, page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /**
     * 处置反馈单：按入口类型校验「判断 → 去向」固定映射后登记判断与去向。
     * <p>
     * 固定映射：zero → need_data/supplement；mismatch → data_bad/block 或 match_bad/strategy。
     * 去向为「屏蔽数据」时同步写入屏蔽列表，相关条目不再下发。
     *
     * @param id  反馈单主键
     * @param dto 处置入参（judgment/disposition/resultNote）
     * @author cmr
     * @since 2026-11-11
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, FeedbackHandleDTO dto) {
        CmrFeedback feedback = requireFeedback(id);
        BizException.assertTrue(CmrFeedback.STATUS_HANDLED.equals(feedback.getStatus()),
                "反馈已处理，不可重复操作");
        String entryType = feedback.getEntryType() != null ? feedback.getEntryType() : feedback.getType();
        validateDispositionMapping(entryType, dto.getJudgment(), dto.getDisposition());
        feedback.setJudgment(dto.getJudgment());
        feedback.setDisposition(dto.getDisposition());
        feedback.setResultNote(dto.getResultNote());
        feedback.setStatus(CmrFeedback.STATUS_HANDLED);
        feedback.setHandlerId(AuthContext.userId());
        if (CmrFeedback.DISPOSITION_BLOCK.equals(dto.getDisposition())) {
            FeedbackVO vo = FeedbackVO.from(feedback);
            for (FeedbackCreateDTO.MismatchItem item : vo.getItems()) {
                searchService.blockItem(item.getItemId(), item.getName(), item.getSource(),
                        dto.getResultNote(), feedback.getId());
            }
            feedback.setBlocked(true);
        }
        feedbackMapper.update(feedback);
        log.info("反馈处置: {} judgment={} disposition={}", feedback.getBizNo(),
                dto.getJudgment(), dto.getDisposition());
    }

    /**
     * 校验「入口类型 → 判断 → 去向」的固定映射。
     * <p>
     * 合法组合：zero → need_data/supplement；mismatch → data_bad/block、match_bad/strategy。
     *
     * @param entryType   入口类型：mismatch/zero
     * @param judgment    处理判断
     * @param disposition 处置去向
     * @author cmr
     * @since 2026-11-11
     */
    private void validateDispositionMapping(String entryType, String judgment, String disposition) {
        boolean legal;
        if (CmrFeedback.ENTRY_ZERO.equals(entryType)) {
            legal = CmrFeedback.JUDGMENT_NEED_DATA.equals(judgment)
                    && CmrFeedback.DISPOSITION_SUPPLEMENT.equals(disposition);
        } else if (CmrFeedback.ENTRY_MISMATCH.equals(entryType)) {
            legal = (CmrFeedback.JUDGMENT_DATA_BAD.equals(judgment)
                    && CmrFeedback.DISPOSITION_BLOCK.equals(disposition))
                    || (CmrFeedback.JUDGMENT_MATCH_BAD.equals(judgment)
                    && CmrFeedback.DISPOSITION_STRATEGY.equals(disposition));
        } else {
            throw new BizException("反馈入口类型非法，无法处置: " + entryType);
        }
        BizException.assertTrue(!legal, "非法处置组合[" + entryType + " → " + judgment + "/" + disposition
                + "]；合法组合为 zero → need_data/supplement、mismatch → data_bad/block 或 match_bad/strategy");
    }

    public CmrFeedback requireFeedback(Long id) {
        CmrFeedback feedback = feedbackMapper.selectOneById(id);
        BizException.assertNull(feedback, "反馈单不存在");
        return feedback;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
