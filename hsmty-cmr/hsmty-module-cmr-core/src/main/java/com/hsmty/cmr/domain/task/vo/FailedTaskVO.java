package com.hsmty.cmr.domain.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失败任务视图（P6）：处置状态由系统自动推进，仅供只读展示。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "失败任务")
public class FailedTaskVO {

    @Schema(description = "任务 ID")
    private Long id;

    @Schema(description = "任务号")
    private String bizNo;

    @Schema(description = "输入内容（经 refId 关联检索历史取得）")
    private String queryText;

    @Schema(description = "失败类型：engine 引擎侧 / data 数据侧")
    private String failType;

    @Schema(description = "失败处置：retry 重试 / isolate 隔离")
    private String failDisposal;

    @Schema(description = "处置进展：retrying 重试中 / recovered 已恢复 / isolated 已转隔离")
    private String handling;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
