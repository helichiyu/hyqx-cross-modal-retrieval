package com.hsmty.cmr.domain.strategy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 策略新建/草稿编辑入参。
 *
 * <p>{@code threshold}、{@code rerank} 允许缺省：新建时分别回落 0.60、true；
 * 编辑时保留库中现值（不再按 0.60 / false 回写）。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class StrategySaveDTO {

    @Schema(description = "策略名称")
    @NotBlank(message = "策略名称不能为空")
    private String name;

    @Schema(description = "适用场景，all 或具体场景")
    @NotBlank(message = "适用场景不能为空")
    private String scene;

    /** {text,image,audio,video} 合计 100 */
    @Schema(description = "四模态权重 {text,image,audio,video}，合计 100；新建缺省 50/30/10/10")
    private Map<String, Integer> weights;

    @Schema(description = "两阶段重排开关；新建缺省 true，编辑缺省保留现值")
    private Boolean rerank;

    @Schema(description = "返回条数 1-100，缺省 20")
    @Min(1)
    @Max(100)
    private Integer topK = 20;

    /** 相似度阈值 0.30-0.99；缺省时新建取 0.60、编辑保留库中现值（null 表示未提供） */
    @Schema(description = "相似度阈值 0.30-0.99；新建缺省 0.60，编辑缺省保留现值")
    @DecimalMin(value = "0.30", message = "阈值不能小于 0.30")
    @DecimalMax(value = "0.99", message = "阈值不能大于 0.99")
    private java.math.BigDecimal threshold;

    @Schema(description = "备注")
    private String remark;
}
