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

    private String queryText;

    private String queryFile;

    private String queryModality;

    private Boolean clarified;

    private String scene;

    private Integer itemCount;

    private Integer elapsedMs;

    private String adoptStatus;

    private Long userId;

    private LocalDateTime createTime;

    public static HistoryVO from(CmrSearchHistory e) {
        return HistoryVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).resultSetId(e.getResultSetId()).taskId(e.getTaskId())
                .queryText(e.getQueryText()).queryFile(e.getQueryFile()).queryModality(e.getQueryModality())
                .clarified(e.getClarified()).scene(e.getScene()).itemCount(e.getItemCount())
                .elapsedMs(e.getElapsedMs()).adoptStatus(e.getAdoptStatus())
                .userId(e.getUserId()).createTime(e.getCreateTime())
                .build();
    }
}
