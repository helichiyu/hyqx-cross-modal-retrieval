package com.hsmty.cmr.domain.operation.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 反馈单（FBK；pending→processing→closed，closed 终态）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_feedback", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrFeedback extends BaseEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_CLOSED = "closed";

    private String bizNo;

    private Long searchId;

    /** inaccurate/ranking/wrong_source/zero_result */
    private String type;

    private String description;

    private String status;

    /** strategy/datasource/backfill */
    private String target;

    private String resultNote;

    private Long handlerId;
}
