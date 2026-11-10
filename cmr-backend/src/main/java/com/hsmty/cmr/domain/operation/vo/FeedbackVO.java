package com.hsmty.cmr.domain.operation.vo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.operation.dto.FeedbackCreateDTO;
import com.hsmty.cmr.domain.operation.entity.CmrFeedback;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 来源任务号 */
    private String sourceTaskNo;

    private String sourceQuery;

    /** 入口类型：mismatch/zero */
    private String entryType;

    /** 勾选的不匹配条目 */
    private List<FeedbackCreateDTO.MismatchItem> items;

    private String description;

    /** pending/handled */
    private String status;

    /** data_bad/match_bad/need_data */
    private String judgment;

    /** block/strategy/supplement */
    private String disposition;

    private Boolean blocked;

    private String resultNote;

    private Long handlerId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static FeedbackVO from(CmrFeedback e) {
        List<FeedbackCreateDTO.MismatchItem> items = e.getMismatchItemsJson() == null
                ? List.of()
                : JsonUtils.read(e.getMismatchItemsJson(),
                        new TypeReference<List<FeedbackCreateDTO.MismatchItem>>() {
                        });
        return FeedbackVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).searchId(e.getSearchId())
                .sourceTaskNo(e.getSourceTaskNo()).sourceQuery(e.getSourceQuery())
                .entryType(e.getEntryType() != null ? e.getEntryType() : e.getType())
                .items(items == null ? List.of() : items)
                .description(e.getDescription()).status(e.getStatus())
                .judgment(e.getJudgment()).disposition(e.getDisposition())
                .blocked(Boolean.TRUE.equals(e.getBlocked()))
                .resultNote(e.getResultNote()).handlerId(e.getHandlerId())
                .createTime(e.getCreateTime()).updateTime(e.getUpdateTime())
                .build();
    }
}
