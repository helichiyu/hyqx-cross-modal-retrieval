package com.hsmty.cmr.domain.evaluation.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评测集（EVAL；labeling→done，未完成不得执行评测）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_eval_set", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrEvalSet extends BaseEntity {

    public static final String STATUS_LABELING = "labeling";
    public static final String STATUS_DONE = "done";

    private String bizNo;

    private String name;

    /** {zeroResult,noClick,reSearch} 至少一项 */
    private String filtersJson;

    private String status;

    private Integer queryCount;
}
