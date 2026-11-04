package com.hsmty.cmr.domain.task.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务（build/eval/search；状态机见 docs/前后端方案/02 第 5 节，失败处置系统自动）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_task", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrTask extends BaseEntity {

    public static final String TYPE_BUILD = "build";
    public static final String TYPE_EVAL = "eval";
    public static final String TYPE_SEARCH = "search";

    public static final String STATUS_READY = "ready";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    /** 失败类型：engine 引擎侧 / data 数据侧 */
    public static final String FAIL_ENGINE = "engine";
    public static final String FAIL_DATA = "data";

    public static final String DISPOSAL_RETRYING = "retrying";
    public static final String DISPOSAL_RECOVERED = "recovered";
    public static final String DISPOSAL_ISOLATED = "isolated";

    private String bizNo;

    private String type;

    /** dataset_id / eval_record_id / search_history_id */
    private Long refId;

    private String status;

    private String failType;

    private String failDisposal;

    private Integer retryCount;

    private String currentStep;

    /** 入参快照 JSON（重放/再次执行用） */
    private String payloadJson;

    private String errorMsg;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;
}
