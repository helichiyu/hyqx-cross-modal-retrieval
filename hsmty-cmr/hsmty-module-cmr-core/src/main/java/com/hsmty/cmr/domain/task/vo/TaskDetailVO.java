package com.hsmty.cmr.domain.task.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 任务详情视图（基本信息 + 步骤日志）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class TaskDetailVO {

    private TaskVO task;

    private List<TaskLogVO> logs;
}
