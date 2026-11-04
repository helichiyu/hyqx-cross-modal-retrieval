package com.hsmty.cmr.domain.dataset.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评定挂起裁决入参
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class AdjudicateDTO {

    @NotNull(message = "裁决结论不能为空")
    private Boolean pass;

    private String note;
}
