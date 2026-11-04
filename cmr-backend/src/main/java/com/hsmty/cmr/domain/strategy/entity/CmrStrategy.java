package com.hsmty.cmr.domain.strategy.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 策略（STG；向量模型段只读回显激活索引，不入策略参数）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_strategy", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrStrategy extends BaseEntity {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACTIVE = "active";

    private String bizNo;

    private String name;

    /** all 或具体场景 */
    private String scene;

    private String status;

    private Integer currentVersion;

    private String remark;
}
