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

import java.time.LocalDateTime;
import java.util.List;

import static com.hsmty.cmr.domain.operation.entity.table.CmrFeedbackTableDef.CMR_FEEDBACK;

/**
 * 反馈单服务（P7）：两个入口（标记不匹配 / 零结果分析）→ 一个判断 → 三个去向（数据流 3.7）
 *
 * @author cmr
 * @since 2026-11-03
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
        if (notBlank(dto.getSourceTaskNo())) {
            history = searchHistoryMapper.selectOneByQuery(QueryWrapper.create()
                    .where("biz_no = ?", dto.getSourceTaskNo()));
        }
        BizException.assertTrue(CmrFeedback.ENTRY_MISMATCH.equals(dto.getEntryType()) && history == null,
                "标记不匹配必须指定有效的来源任务");

        CmrFeedback feedback = new CmrFeedback();
        feedback.setId(idWorker.next());
        feedback.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_FEEDBACK));
        feedback.setSearchId(history == null ? null : history.getId());
        feedback.setSourceTaskNo(dto.getSourceTaskNo());
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
        log.info("反馈登记: {} entry={} task={}", feedback.getBizNo(), dto.getEntryType(), dto.getSourceTaskNo());
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
     * 处置：登记判断与去向；去向为“屏蔽数据”时同步写入屏蔽列表，条目不再下发
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, FeedbackHandleDTO dto) {
        CmrFeedback feedback = requireFeedback(id);
        BizException.assertTrue(CmrFeedback.STATUS_HANDLED.equals(feedback.getStatus()),
                "反馈已处理，不可重复操作");
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

    public CmrFeedback requireFeedback(Long id) {
        CmrFeedback feedback = feedbackMapper.selectOneById(id);
        BizException.assertNull(feedback, "反馈单不存在");
        return feedback;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
