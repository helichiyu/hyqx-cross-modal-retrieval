package com.hsmty.cmr.domain.search.vo;

import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检索历史视图（P9）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class HistoryVO {

    private Long id;

    private String bizNo;

    private String resultSetId;

    private Long taskId;

    private Long indexId;

    private String taskBizNo;

    private String taskStatus;

    private String queryText;

    private String queryFile;

    private String queryModality;

    private String targetModality;

    private Boolean clarified;

    private String scene;

    private Integer itemCount;

    private Integer elapsedMs;

    private String adoptStatus;

    private Long userId;

    private Long operatorId;

    private String operatorName;

    private String strategyNo;

    private Integer strategyVersion;

    private java.math.BigDecimal threshold;

    /** T=已封存（赞/踩锁定） */
    private String sealed;

    private String replayOfTaskNo;

    private LocalDateTime createTime;

    /**
     * PO 转 VO。
     *
     * @param e             检索历史
     * @param taskBizNo     任务号（TASK 形态）
     * @param taskStatus    任务状态
     * @param replayTaskNo  重放来源任务号（对外统一 TASK 形态，可为 null）
     * @return 检索历史出参
     */
    public static HistoryVO from(CmrSearchHistory e, String taskBizNo, String taskStatus, String replayTaskNo) {
        return HistoryVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).resultSetId(e.getResultSetId()).taskId(e.getTaskId())
                .indexId(e.getIndexId()).taskBizNo(taskBizNo).taskStatus(taskStatus)
                .queryText(e.getQueryText()).queryFile(e.getQueryFile()).queryModality(e.getQueryModality())
                .targetModality(e.getTargetModality())
                .clarified(e.getClarified()).scene(e.getScene()).itemCount(e.getItemCount())
                .elapsedMs(e.getElapsedMs()).adoptStatus(e.getAdoptStatus())
                .userId(e.getUserId())
                .operatorId(e.getOperatorId()).operatorName(e.getOperatorName())
                .strategyNo(e.getStrategyNo()).strategyVersion(e.getStrategyVersion())
                .threshold(e.getThreshold()).sealed(e.getSealed())
                .replayOfTaskNo(replayTaskNo)
                .createTime(e.getCreateTime())
                .build();
    }
}
