package com.hsmty.cmr.domain.operation.service;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.auth.AuthContext;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.operation.dto.FeedbackCreateDTO;
import com.hsmty.cmr.domain.operation.entity.CmrFeedback;
import com.hsmty.cmr.domain.operation.mapper.FeedbackMapper;
import com.hsmty.cmr.domain.operation.vo.FeedbackStatsVO;
import com.hsmty.cmr.domain.operation.vo.FeedbackVO;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.hsmty.cmr.domain.operation.entity.table.CmrFeedbackTableDef.CMR_FEEDBACK;

/**
 * 反馈单服务：登记（四类型）→ 处理（回填去向）→ 闭环（数据流 3.6）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Set<String> TYPES = Set.of("inaccurate", "ranking", "wrong_source", "zero_result");
    private static final Set<String> TARGETS = Set.of("strategy", "datasource", "backfill");

    private final FeedbackMapper feedbackMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    @Transactional(rollbackFor = Exception.class)
    public FeedbackVO create(FeedbackCreateDTO dto) {
        BizException.assertTrue(!TYPES.contains(dto.getType()), "非法反馈类型: " + dto.getType());
        CmrSearchHistory history = searchHistoryMapper.selectOneById(dto.getSearchId());
        BizException.assertNull(history, "关联检索不存在");

        CmrFeedback feedback = new CmrFeedback();
        feedback.setId(idWorker.next());
        feedback.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_FEEDBACK));
        feedback.setSearchId(dto.getSearchId());
        feedback.setType(dto.getType());
        feedback.setDescription(dto.getDescription());
        feedback.setStatus(CmrFeedback.STATUS_PENDING);
        feedback.setDelFlag("F");
        feedback.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(feedback, true);
        log.info("反馈登记: {} type={} search={}", feedback.getBizNo(), dto.getType(), history.getBizNo());
        return FeedbackVO.from(feedback, history.getBizNo());
    }

    public FeedbackStatsVO stats() {
        return FeedbackStatsVO.builder()
                .total(count(null))
                .pending(count("pending"))
                .processing(count("processing"))
                .closed(count("closed"))
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

    public PageResult<FeedbackVO> page(PageQuery query, String type, String status, Long searchId) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_FEEDBACK);
        if (searchId != null) {
            qw.where(CMR_FEEDBACK.SEARCH_ID.eq(searchId));
        }
        if (notBlank(type)) {
            qw.where(CMR_FEEDBACK.TYPE.eq(type));
        }
        if (notBlank(status)) {
            qw.where(CMR_FEEDBACK.STATUS.eq(status));
        }
        qw.orderBy(CMR_FEEDBACK.ID.desc());
        Page<CmrFeedback> page = feedbackMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        List<FeedbackVO> vos = page.getRecords().stream()
                .map(f -> FeedbackVO.from(f, searchBizNo(f.getSearchId())))
                .toList();
        return PageResult.of(vos, page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /** 开始处理：pending→processing，回填去向 */
    @Transactional(rollbackFor = Exception.class)
    public void process(Long id, String target) {
        CmrFeedback feedback = requireFeedback(id);
        BizException.assertTrue(!CmrFeedback.STATUS_PENDING.equals(feedback.getStatus()),
                "仅待处理反馈可开始处理");
        if (notBlank(target)) {
            BizException.assertTrue(!TARGETS.contains(target), "非法去向: " + target);
            feedback.setTarget(target);
        }
        feedback.setStatus(CmrFeedback.STATUS_PROCESSING);
        feedback.setHandlerId(AuthContext.userId());
        feedbackMapper.update(feedback);
    }

    /** 闭环：processing/pending→closed（终态） */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String resultNote) {
        CmrFeedback feedback = requireFeedback(id);
        BizException.assertTrue(CmrFeedback.STATUS_CLOSED.equals(feedback.getStatus()),
                "反馈已闭环，不可重复操作");
        feedback.setStatus(CmrFeedback.STATUS_CLOSED);
        feedback.setResultNote(resultNote);
        if (feedback.getHandlerId() == null) {
            feedback.setHandlerId(AuthContext.userId());
        }
        feedbackMapper.update(feedback);
    }

    public CmrFeedback requireFeedback(Long id) {
        CmrFeedback feedback = feedbackMapper.selectOneById(id);
        BizException.assertNull(feedback, "反馈单不存在");
        return feedback;
    }

    private String searchBizNo(Long searchId) {
        if (searchId == null) {
            return null;
        }
        CmrSearchHistory h = searchHistoryMapper.selectOneById(searchId);
        return h == null ? null : h.getBizNo();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
