package com.hsmty.cmr.domain.operation.vo;

import tools.jackson.core.type.TypeReference;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.operation.dto.FeedbackCreateDTO;
import com.hsmty.cmr.domain.operation.entity.CmrFeedback;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈单视图（P7）
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
public class FeedbackVO {

    @Schema(description = "反馈单主键")
    private Long id;

    @Schema(description = "反馈单业务号")
    private String bizNo;

    @Schema(description = "关联检索记录主键")
    private Long searchId;

    /** 来源任务号 */
    @Schema(description = "来源任务号")
    private String sourceTaskNo;

    @Schema(description = "来源搜索词")
    private String sourceQuery;

    /** 入口类型：mismatch/zero */
    @Schema(description = "入口类型：mismatch/zero")
    private String entryType;

    /** 勾选的不匹配条目 */
    @Schema(description = "勾选的不匹配条目")
    private List<FeedbackCreateDTO.MismatchItem> items;

    @Schema(description = "反馈描述")
    private String description;

    /** pending/handled */
    @Schema(description = "状态：pending/handled")
    private String status;

    /** data_bad/match_bad/need_data */
    @Schema(description = "处理判断：data_bad/match_bad/need_data，未处理时为空")
    private String judgment;

    /** block/strategy/supplement；未处理时为 null，前端显示「—」 */
    @Schema(description = "处置去向：block/strategy/supplement；未处理时为 null")
    private String disposition;

    @Schema(description = "是否已写入屏蔽列表")
    private Boolean blocked;

    @Schema(description = "处置备注")
    private String resultNote;

    @Schema(description = "处理人 ID")
    private Long handlerId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 将反馈单实体转换为视图。
     * <p>
     * 未处理（pending）的反馈单一律返回 disposition=null，避免把历史遗留或默认值（如 strategy）当作真实处置去向；
     * 前端据此显示「—」。
     *
     * @param e 反馈单实体
     * @return 反馈单视图
     * @author cmr
     * @since 2026-11-11
     */
    public static FeedbackVO from(CmrFeedback e) {
        List<FeedbackCreateDTO.MismatchItem> items = e.getMismatchItemsJson() == null
                ? List.of()
                : JsonUtils.read(e.getMismatchItemsJson(),
                        new TypeReference<List<FeedbackCreateDTO.MismatchItem>>() {
                        });
        boolean handled = CmrFeedback.STATUS_HANDLED.equals(e.getStatus());
        return FeedbackVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).searchId(e.getSearchId())
                .sourceTaskNo(e.getSourceTaskNo()).sourceQuery(e.getSourceQuery())
                .entryType(e.getEntryType() != null ? e.getEntryType() : e.getType())
                .items(items == null ? List.of() : items)
                .description(e.getDescription()).status(e.getStatus())
                .judgment(e.getJudgment())
                .disposition(handled ? e.getDisposition() : null)
                .blocked(Boolean.TRUE.equals(e.getBlocked()))
                .resultNote(e.getResultNote()).handlerId(e.getHandlerId())
                .createTime(e.getCreateTime()).updateTime(e.getUpdateTime())
                .build();
    }
}
