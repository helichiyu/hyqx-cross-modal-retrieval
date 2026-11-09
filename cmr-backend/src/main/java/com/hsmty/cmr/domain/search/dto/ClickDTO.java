package com.hsmty.cmr.domain.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 结果条目点击埋点入参。
 */
@Data
@Schema(description = "结果条目点击埋点")
public class ClickDTO {

    @Schema(description = "结果条目排序位次", example = "1")
    @NotNull(message = "rank 不能为空")
    @Min(value = 1, message = "rank 必须大于等于 1")
    private Integer rank;
}