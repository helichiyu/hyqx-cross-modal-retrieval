package com.hsmty.cmr.domain.evaluation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评测执行入参（评测对象=策略版本，空则系统默认）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public class EvalRunDTO {

    @NotNull(message = "评测集不能为空")
    private Long evalSetId;

    /** 评测对象策略版本（空=系统默认/生效策略） */
    private Long targetStrategyVersionId;
}
