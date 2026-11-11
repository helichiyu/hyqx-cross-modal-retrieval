package com.hsmty.cmr.domain.strategy.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 策略版本视图
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class StrategyVersionVO {

    private Long id;

    private Long strategyId;

    private Integer versionNo;

    private String weightsJson;

    private Boolean rerank;

    private Integer topK;

    private java.math.BigDecimal threshold;

    private String status;

    private String source;

    private LocalDateTime publishedTime;

    private LocalDateTime createTime;
}
