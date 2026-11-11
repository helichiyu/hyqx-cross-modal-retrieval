package com.hsmty.cmr.domain.evaluation.dto;

import lombok.Data;

/**
 * 评测重放入参：可指定待比较的策略（不传用基准快照）
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
public class EvaluationReplayDTO {

    private Long strategyId;
}
