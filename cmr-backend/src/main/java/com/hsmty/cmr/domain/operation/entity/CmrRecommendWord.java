package com.hsmty.cmr.domain.operation.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 推荐词（REC；enabled 开=进检索台联想）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_recommend_word", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrRecommendWord extends BaseEntity {

    private String bizNo;

    private String word;

    /** all 或具体场景 */
    private String scene;

    private Boolean enabled;

    private Integer sort;
}
