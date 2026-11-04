package com.hsmty.cmr.domain.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 评测集创建入参（三类筛选至少选一项）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class EvalSetCreateDTO {

    @NotBlank(message = "评测集名称不能为空")
    private String name;

    /** {zeroResult,noClick,reSearch} 至少一项为 true */
    private Map<String, Boolean> filters;
}
