package com.hsmty.cmr.domain.operation.vo;

import com.hsmty.cmr.domain.operation.entity.CmrFeedback;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈单视图（P7）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class FeedbackVO {

    private Long id;

    private String bizNo;

    private Long searchId;

    /** 关联检索的 SRH 编号（跳转用） */
    private String searchBizNo;

    private String type;

    private String description;

    private String status;

    private String target;

    private String resultNote;

    private Long handlerId;

    private LocalDateTime createTime;

    public static FeedbackVO from(CmrFeedback e, String searchBizNo) {
        return FeedbackVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).searchId(e.getSearchId()).searchBizNo(searchBizNo)
                .type(e.getType()).description(e.getDescription()).status(e.getStatus())
                .target(e.getTarget()).resultNote(e.getResultNote()).handlerId(e.getHandlerId())
                .createTime(e.getCreateTime())
                .build();
    }
}
