package com.hsmty.cmr.domain.evaluation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 效果评测单次运行指标（P8 契约第 7 节，基准与每次重放共用）。
 *
 * <p>字段均为可选语义：旧数据或结果摘要缺失时返回 {@code null}，前端显示「—」，不报错。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "效果评测单次运行指标")
public class EvalMetricsVO {

    @Schema(description = "任务号")
    private String taskNo;

    @Schema(description = "运行时间")
    private LocalDateTime time;

    @Schema(description = "结果数")
    private Integer items;

    @Schema(description = "踩数")
    private Integer down;

    @Schema(description = "踩率 = 踩数 / 结果数；结果数为 0 或未知时为 null")
    private Double downRate;

    @Schema(description = "Top-20 平均相似度")
    private Double top20AvgSim;

    @Schema(description = "Top-1 相似度")
    private Double top1Sim;

    @Schema(description = "Top-20 与基准的条目重叠率（itemId 交集 / 20）")
    private Double top20Overlap;

    @Schema(description = "全部结果的平均相似度")
    private Double avgSim;

    @Schema(description = "本次检索耗时（毫秒）")
    private Integer elapsedMs;
}
