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

    /** 下线备注 */
    private String offlineRemark;

    private Integer currentVersion;

    /** 参数摘要，如 “阈值 0.60 · 重排开” */
    private String paramSummary;

    /** 是否存在待发布草稿（列表「发布」按钮据此启用；无草稿时后端必报错） */
    private Boolean hasDraft;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
