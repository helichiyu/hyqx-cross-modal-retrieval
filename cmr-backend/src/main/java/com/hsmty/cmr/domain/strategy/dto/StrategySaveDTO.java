package com.hsmty.cmr.domain.strategy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 策略新建/草稿编辑入参
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class StrategySaveDTO {

    @NotBlank(message = "策略名称不能为空")
    private String name;

    @NotBlank(message = "适用场景不能为空")
    private String scene;

    /** {text,image,audio,video} 合计 100 */
    private Map<String, Integer> weights;

    private Boolean rerank = false;

    @Min(1)
    @Max(100)
    private Integer topK = 20;

    /** 相似度阈值 0.30-0.99，默认 0.60 */
    @DecimalMin(value = "0.30", message = "阈值不能小于 0.30")
    @DecimalMax(value = "0.99", message = "阈值不能大于 0.99")
    private java.math.BigDecimal threshold = new java.math.BigDecimal("0.60");

    private String remark;
}
