package com.hsmty.cmr.domain.evaluation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 效果评测详情（P8）：基准 vs 最近重放 + 退化原因 + 数据检查。
 *
 * <p>{@code base}/{@code latest}/{@code replays} 在保留既有字段（searchId、itemCount、downCount、
 * strategyNo、strategyVersion、threshold 等）的基础上，追加 {@link EvalMetricsVO} 的指标字段；
 * 新增指标为可选语义，{@code null} 表示未知。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "效果评测详情")
public class EvaluationDetailVO {

    @Schema(description = "评测主键")
    private Long id;

    @Schema(description = "评测编号")
    private String bizNo;

    @Schema(description = "评测 query")
    private String query;

    @Schema(description = "基准检索指标（既有字段 + EvalMetrics 指标）")
    private Map<String, Object> base;

    @Schema(description = "重放检索指标列表（按时间倒序，既有字段 + EvalMetrics 指标）")
    private List<Map<String, Object>> replays;

    @Schema(description = "最近一次重放指标；无重放时为 null")
    private Map<String, Object> latest;

    @Schema(description = "结论：degraded/normal/unevaluated")
    private String conclusion;

    @Schema(description = "退化原因列表")
    private List<String> reasons;

    @Schema(description = "踩率变化（百分点，重放 - 基准）")
    private Double downRateDelta;

    @Schema(description = "结果数下降比例（百分比）")
    private Double resultDropRatio;

    @Schema(description = "检查评测数据 4 项只读结果")
    private List<EvaluationCheckVO> checks;
}
