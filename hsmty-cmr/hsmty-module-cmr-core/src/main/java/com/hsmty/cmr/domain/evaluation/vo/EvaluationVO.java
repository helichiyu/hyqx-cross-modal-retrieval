package com.hsmty.cmr.domain.evaluation.vo;

import com.hsmty.cmr.domain.evaluation.entity.CmrEvaluation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评测登记出参：只暴露业务字段，不直接下发 PO。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Data
@Builder
@Schema(description = "效果评测")
public class EvaluationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "评测编号")
    private String bizNo;

    @Schema(description = "评测 query")
    private String query;

    @Schema(description = "基准检索历史 ID")
    private Long baseSearchId;

    @Schema(description = "基准任务号（SRH）")
    private String baseTaskNo;

    @Schema(description = "基准检索时间")
    private LocalDateTime baseTime;

    @Schema(description = "基准结果条数")
    private Integer baseN;

    @Schema(description = "基准踩数")
    private Integer baseDown;

    /**
     * PO 转 VO。
     *
     * @param entity 评测实体
     * @return 评测出参
     */
    public static EvaluationVO from(CmrEvaluation entity) {
        if (entity == null) {
            return null;
        }
        return EvaluationVO.builder()
                .id(entity.getId())
                .bizNo(entity.getBizNo())
                .query(entity.getQuery())
                .baseSearchId(entity.getBaseSearchId())
                .baseTaskNo(entity.getBaseTaskNo())
                .baseTime(entity.getBaseTime())
                .baseN(entity.getBaseN())
                .baseDown(entity.getBaseDown())
                .build();
    }
}
