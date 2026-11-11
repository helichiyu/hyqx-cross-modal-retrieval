package com.hsmty.cmr.domain.evaluation.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 效果评测：一条 query 一条评测，基准取历史检索，重放按 query 归集（数据流 3.6）
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_evaluation", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrEvaluation extends BaseEntity {

    private String bizNo;

    private String query;

    private Long baseSearchId;

    private String baseTaskNo;

    private LocalDateTime baseTime;

    private Integer baseN;

    private Integer baseDown;
}
