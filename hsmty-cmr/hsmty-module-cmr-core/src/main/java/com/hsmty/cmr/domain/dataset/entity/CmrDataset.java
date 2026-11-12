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

    /** 状态机：ingest → stored → indexing → searchable（无人工卡点，无 scoring/stuck/rejected） */
    public static final String STATUS_INGEST = "ingest";
    public static final String STATUS_STORED = "stored";
    public static final String STATUS_INDEXING = "indexing";
    public static final String STATUS_SEARCHABLE = "searchable";

    private String bizNo;

    private Long datasourceId;

    /** 上游数据集 ID（接入去重锚点；手工登记为空） */
    private String upstreamDatasetId;

    private String name;

    private String version;

    private String scene;

    private String status;

    /**
     * 历史卡点原因，仅保留列兼容存量数据；建库链已无人工卡点，新数据恒为 null。
     *
     * @deprecated 402 评定/406 投产校验下线后不再写入
     */
    @Deprecated
    private String stuckReason;

    /** 数据源逻辑目录 dataset/ds/{bizNo}/{datasetId}（回源模式仅存 manifest，不存原始内容） */
    private String fileRoot;

    /** 401 报告摘要 JSON */
    private String metaJson;

    /**
     * 历史 402 评定分，仅保留列兼容存量数据；新数据恒为 null。
     *
     * @deprecated 402 评定已从建库链移除
     */
    @Deprecated
    private BigDecimal score;

    /**
     * 历史评定结论 pass/suspend/reject，仅保留列兼容存量数据。
     *
     * @deprecated 402 评定已从建库链移除
     */
    @Deprecated
    private String decision;

    private Integer itemCount;

    /** 分模态条数 JSON */
    private String modalityJson;

    private Long currentIndexId;
}
