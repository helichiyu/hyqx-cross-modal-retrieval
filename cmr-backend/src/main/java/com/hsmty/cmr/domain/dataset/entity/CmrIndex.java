package com.hsmty.cmr.domain.dataset.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 向量索引（candidate/active/retired；同场景同时仅一条 active）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_index", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrIndex extends BaseEntity {

    public static final String STATUS_CANDIDATE = "candidate";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RETIRED = "retired";

    private Long datasetId;

    /** 逻辑目录 runtime/index/... */
    private String indexDir;

    private String modelVersion;

    private String status;

    private String countsJson;

    /** 406 投产校验结果 JSON */
    private String checkResultJson;

    private LocalDateTime activatedTime;
}
