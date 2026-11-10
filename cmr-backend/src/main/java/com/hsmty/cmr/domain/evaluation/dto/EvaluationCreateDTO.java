package com.hsmty.cmr.domain.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 效果评测入参：以某次检索为基准
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
public class EvaluationCreateDTO {

    @NotBlank(message = "基准任务号不能为空")
    private String baseTaskNo;
}
