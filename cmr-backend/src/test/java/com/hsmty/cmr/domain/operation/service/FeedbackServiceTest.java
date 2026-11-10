package com.hsmty.cmr.domain.operation.service;

import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.operation.dto.FeedbackCreateDTO;
import com.hsmty.cmr.domain.operation.dto.FeedbackHandleDTO;
import com.hsmty.cmr.domain.operation.entity.CmrFeedback;
import com.hsmty.cmr.domain.operation.mapper.FeedbackMapper;
import com.hsmty.cmr.domain.operation.vo.FeedbackVO;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.search.service.SearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结果运营规则：重复标记不匹配幂等、屏蔽去向同步写屏蔽列表
 *
 * @author cmr
 * @since 2026-11-10
 */
class FeedbackServiceTest {

    private final FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
    private final SearchHistoryMapper historyMapper = mock(SearchHistoryMapper.class);
    private final SearchService searchService = mock(SearchService.class);

    @Test
    void 同一任务重复标记不匹配返回已有待处理单() {
        CmrSearchHistory history = new CmrSearchHistory();
        history.setId(11L);
        history.setBizNo("SRH-TASK-1");
        when(historyMapper.selectOneByQuery(any())).thenReturn(history);

        CmrFeedback pending = new CmrFeedback();
        pending.setId(99L);
        pending.setBizNo("FBK-EXISTS");
        pending.setStatus(CmrFeedback.STATUS_PENDING);
        pending.setEntryType(CmrFeedback.ENTRY_MISMATCH);
        when(feedbackMapper.selectOneByQuery(any())).thenReturn(pending);

        FeedbackVO vo = service().create(mismatchDTO());

        assertThat(vo.getId()).isEqualTo(99L);
        assertThat(vo.getBizNo()).isEqualTo("FBK-EXISTS");
        verify(feedbackMapper, never()).insert(any(), anyBoolean());
    }

    @Test
    void 屏蔽去向写入屏蔽列表并标记已处理() {
        CmrFeedback feedback = new CmrFeedback();
        feedback.setId(5L);
        feedback.setBizNo("FBK-BLOCK");
        feedback.setStatus(CmrFeedback.STATUS_PENDING);
        feedback.setEntryType(CmrFeedback.ENTRY_MISMATCH);
        feedback.setMismatchItemsJson("[{\"itemId\":\"it-1\",\"name\":\"样例\",\"source\":\"ds-a\"}]");
        when(feedbackMapper.selectOneById(5L)).thenReturn(feedback);

        FeedbackHandleDTO dto = new FeedbackHandleDTO();
        dto.setJudgment("data_bad");
        dto.setDisposition(CmrFeedback.DISPOSITION_BLOCK);
        dto.setResultNote("内容错误");
        service().handle(5L, dto);

        verify(searchService).blockItem("it-1", "样例", "ds-a", "内容错误", 5L);
        assertThat(feedback.getBlocked()).isTrue();
        assertThat(feedback.getStatus()).isEqualTo(CmrFeedback.STATUS_HANDLED);
        verify(feedbackMapper).update(feedback);
    }

    private FeedbackService service() {
        return new FeedbackService(feedbackMapper, historyMapper, searchService,
                new SnowflakeIdWorker(1, 1), new BizNoGenerator());
    }

    private FeedbackCreateDTO mismatchDTO() {
        FeedbackCreateDTO dto = new FeedbackCreateDTO();
        dto.setSourceTaskNo("SRH-TASK-1");
        dto.setEntryType(CmrFeedback.ENTRY_MISMATCH);
        FeedbackCreateDTO.MismatchItem item = new FeedbackCreateDTO.MismatchItem();
        item.setItemId("it-1");
        item.setName("样例");
        item.setSource("ds-a");
        dto.setItems(List.of(item));
        return dto;
    }
}
