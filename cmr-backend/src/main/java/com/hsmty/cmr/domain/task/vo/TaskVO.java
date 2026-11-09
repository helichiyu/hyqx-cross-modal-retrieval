package com.hsmty.cmr.domain.task.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务视图（P6 列表/P3 详情）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class TaskVO {

    private Long id;

    private String bizNo;

    private String type;

    private Long refId;

    /** 关联对象业务编号（DS/EVAL/SRH），用于跳转 */
    private String refBizNo;

    /** 关联对象前端路由：build=/library，search=/search/results/{rst}，eval=/evaluation */
    private String refRoute;

    private String status;

    private String failType;

    private String failDisposal;

    private Integer retryCount;

    private String currentStep;

    private String errorMsg;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;

    private LocalDateTime createTime;
}
