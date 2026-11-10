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

    /** 系统预置（兜底），不可下线 */
    private Boolean preset;

    private String offlineReason;

    private LocalDateTime offlineTime;

    private Integer currentVersion;

    /** 参数摘要，如 “阈值 0.60 · 重排开” */
    private String paramSummary;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
