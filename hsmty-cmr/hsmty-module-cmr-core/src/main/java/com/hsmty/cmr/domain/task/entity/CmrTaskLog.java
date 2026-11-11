package com.hsmty.cmr.domain.task.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务步骤日志（P3 材料：各环节 outputFile 可经 /file 查看）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_task_log", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrTaskLog extends BaseEntity {

    public static final String STATUS_START = "start";
    public static final String STATUS_OK = "ok";
    public static final String STATUS_FAIL = "fail";

    private Long taskId;

    /** 401/402/.../406/eval */
    private String step;

    private String status;

    /** 算子响应摘要 JSON */
    private String operatorOutputJson;

    private String message;
}
