package com.hsmty.cmr.domain.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 反馈登记入参（P3 摘要「标记不匹配」/ P2 零结果「发起原因分析」）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class FeedbackCreateDTO {

    /** 来源任务号（P3 必填；P2 零结果可为空） */
    private String sourceTaskNo;

    /** 来源搜索词 */
    private String sourceQuery;

    /** 入口类型：mismatch/zero，只读 */
    @NotBlank(message = "反馈入口类型不能为空")
    @Pattern(regexp = "mismatch|zero", message = "入口类型只能是 mismatch/zero")
    private String entryType;

    /** 勾选的不匹配条目（mismatch 入口） */
    private List<MismatchItem> items;

    private String description;

    /**
     * 不匹配条目
     */
    @Data
    public static class MismatchItem {

        private String itemId;

        private String name;

        private String source;
    }
}
