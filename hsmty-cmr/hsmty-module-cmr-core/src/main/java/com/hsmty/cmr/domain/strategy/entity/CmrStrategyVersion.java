package com.hsmty.cmr.domain.strategy.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 策略版本（发布递增立即生效；回滚产生 source=rollback 的新版本记录）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_strategy_version", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrStrategyVersion extends BaseEntity {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ROLLBACK = "rollback";

    private Long strategyId;

    private Integer versionNo;

    /** {text,image,audio,video} 合计 100 */
    private String weightsJson;

    private Boolean rerank;

    private Integer topK;

    /** 相似度阈值 0.30-0.99，默认 0.60 */
    private java.math.BigDecimal threshold;

    private String status;

    /** new/rollback */
    private String source;

    private LocalDateTime publishedTime;
}
