package com.hsmty.cmr.domain.strategy.dto;

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

    private String remark;
}
