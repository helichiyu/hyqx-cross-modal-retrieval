package com.hsmty.cmr.domain.strategy.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 策略视图（P5 列表）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class StrategyVO {

    private Long id;

    private String bizNo;

    private String name;

    private String scene;

    private String status;

    private Integer currentVersion;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
