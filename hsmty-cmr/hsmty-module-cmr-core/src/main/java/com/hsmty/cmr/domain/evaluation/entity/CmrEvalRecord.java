package com.hsmty.cmr.domain.evaluation.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评测历史（409 执行结果与对比）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_eval_record", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrEvalRecord extends BaseEntity {

    private String bizNo;

    private Long evalSetId;

    private Long indexId;

    /** 评测对象策略版本（空=系统默认） */
    private Long strategyVersionId;

    private Long taskId;

    /** {recall@K,precision@K,ndcg@K} */
    private String metricsJson;

    private String linesJson;

    /** pass/fail */
    private String conclusion;

    private String failedLinesJson;

    /** 与上次差值+improved */
    private String comparisonJson;

    private String perQueryJson;
}
