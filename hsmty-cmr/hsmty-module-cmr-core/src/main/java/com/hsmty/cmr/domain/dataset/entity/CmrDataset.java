package com.hsmty.cmr.domain.dataset.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 数据集（库管理主状态机，见 docs/前后端方案/02 第 3 节）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_dataset", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrDataset extends BaseEntity {

    /** 状态机 */
    public static final String STATUS_INGEST = "ingest";
    public static final String STATUS_SCORING = "scoring";
    public static final String STATUS_STORED = "stored";
    public static final String STATUS_INDEXING = "indexing";
    public static final String STATUS_SEARCHABLE = "searchable";
    public static final String STATUS_STUCK = "stuck";
    public static final String STATUS_REJECTED = "rejected";

    private String bizNo;

    private Long datasourceId;

    private String name;

    private String version;

    private String scene;

    private String status;

    private String stuckReason;

    /** 原始文件逻辑目录 dataset/ds/{bizNo} */
    private String fileRoot;

    /** 401 报告摘要 JSON */
    private String metaJson;

    private BigDecimal score;

    /** pass/suspend/reject */
    private String decision;

    private Integer itemCount;

    /** 分模态条数 JSON */
    private String modalityJson;

    private Long currentIndexId;
}
