package com.hsmty.cmr.domain.task.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务步骤日志视图（P3 材料：operatorOutputJson 内 outputFile 可经 /file 查看）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class TaskLogVO {

    private Long id;

    private Long taskId;

    private String step;

    private String status;

    private String operatorOutputJson;

    private String message;

    private LocalDateTime createTime;
}
