package com.hsmty.cmr.domain.evaluation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 评测数据检查项（P8「检查评测数据」，只读，不改任何数据）。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "评测数据检查项")
public class EvaluationCheckVO {

    @Schema(description = "检查项标识")
    private String key;

    @Schema(description = "检查项名称")
    private String label;

    @Schema(description = "是否通过")
    private Boolean ok;

    @Schema(description = "补充说明；无补充说明时为 null")
    private String detail;
}
