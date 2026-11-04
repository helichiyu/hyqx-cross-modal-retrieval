package com.hsmty.cmr.domain.evaluation.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测历史视图（P8：结论/指标/对比）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class EvalRecordVO {

    private Long id;

    private String bizNo;

    private Long evalSetId;

    private Long indexId;

    private Long strategyVersionId;

    private Long taskId;

    private String taskBizNo;

    private String metricsJson;

    private String linesJson;

    private String conclusion;

    private String failedLinesJson;

    private String comparisonJson;

    private LocalDateTime createTime;
}
