package com.hsmty.cmr.domain.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 反馈登记入参（P2 结果页入口）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class FeedbackCreateDTO {

    @NotNull(message = "关联检索不能为空")
    private Long searchId;

    /** inaccurate/ranking/wrong_source/zero_result */
    @NotBlank(message = "反馈类型不能为空")
    private String type;

    private String description;
}
