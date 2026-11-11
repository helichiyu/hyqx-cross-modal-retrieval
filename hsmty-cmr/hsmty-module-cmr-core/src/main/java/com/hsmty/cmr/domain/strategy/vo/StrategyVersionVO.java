package com.hsmty.cmr.domain.strategy.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 策略版本视图。
 *
 * <p>{@code name} 为发布时的策略名快照；历史版本未落快照时为 {@code null}。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class StrategyVersionVO {

    @Schema(description = "版本主键")
    private Long id;

    @Schema(description = "所属策略主键")
    private Long strategyId;

    @Schema(description = "发布时的策略名快照，历史数据可能为空")
    private String name;

    @Schema(description = "版本号")
    private Integer versionNo;

    @Schema(description = "四模态权重 JSON")
    private String weightsJson;

    @Schema(description = "两阶段重排开关")
    private Boolean rerank;

    @Schema(description = "返回条数")
    private Integer topK;

    @Schema(description = "相似度阈值 0.30-0.99")
    private java.math.BigDecimal threshold;

    @Schema(description = "版本状态 draft/published/rollback")
    private String status;

    @Schema(description = "来源 new/rollback")
    private String source;

    @Schema(description = "发布时间")
    private LocalDateTime publishedTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
