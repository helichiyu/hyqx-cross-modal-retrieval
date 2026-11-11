package com.hsmty.cmr.domain.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 搜索行为流水视图（P6）：任务级流水，含赞踩计数、封存状态与重放来源。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "搜索行为流水")
public class BehaviorVO {

    @Schema(description = "检索历史 ID")
    private Long id;

    @Schema(description = "任务号（TASK，对外展示）")
    private String taskNo;

    @Schema(description = "检索号（SRH，内部业务号）")
    private String srhNo;

    @Schema(description = "结果集编号")
    private String resultSetId;

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务业务编号")
    private String taskBizNo;

    @Schema(description = "任务状态")
    private String taskStatus;

    @Schema(description = "输入内容")
    private String queryText;

    @Schema(description = "查询文件路径；文件检索时用于展示文件名")
    private String queryFile;

    @Schema(description = "查询模态")
    private String queryModality;

    @Schema(description = "目标模态；为空表示不限定目标模态")
    private String targetModality;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "策略编号")
    private String strategyNo;

    @Schema(description = "策略版本")
    private Integer strategyVersion;

    @Schema(description = "相似度阈值")
    private BigDecimal threshold;

    @Schema(description = "结果条数")
    private Integer itemCount;

    @Schema(description = "耗时（毫秒）")
    private Integer elapsedMs;

    @Schema(description = "是否已封存")
    private Boolean sealed;

    @Schema(description = "是否零结果")
    private Boolean zeroResult;

    @Schema(description = "赞数")
    private Long upCount;

    @Schema(description = "踩数")
    private Long downCount;

    @Schema(description = "重放来源任务号；普通检索为空")
    private String replayOfTaskNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
