package com.hsmty.cmr.domain.evaluation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 难例样本（P8）：有点踩的检索任务，同时作为「新建评测」的候选记录。
 *
 * <p>被踩条目数按条目 ID 去重，不统计点踩次数。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "难例样本")
public class HardCaseVO {

    @Schema(description = "任务号")
    private String taskNo;

    @Schema(description = "查询内容；文件检索时为 null")
    private String query;

    @Schema(description = "被踩条目数（按 itemId 去重）")
    private Integer downCount;

    @Schema(description = "结果集编号")
    private String resultSetId;

    @Schema(description = "任务创建时间")
    private LocalDateTime createTime;
}
