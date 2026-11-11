package com.hsmty.cmr.domain.dataset.vo;

import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 索引视图（candidate/active/retired + 406 结果）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class IndexVO {

    private Long id;

    private Long datasetId;

    private String indexDir;

    private String modelVersion;

    private String status;

    private String countsJson;

    private String checkResultJson;

    private LocalDateTime activatedTime;

    private LocalDateTime createTime;

    public static IndexVO from(CmrIndex e) {
        return IndexVO.builder()
                .id(e.getId()).datasetId(e.getDatasetId()).indexDir(e.getIndexDir())
                .modelVersion(e.getModelVersion()).status(e.getStatus())
                .countsJson(e.getCountsJson()).checkResultJson(e.getCheckResultJson())
                .activatedTime(e.getActivatedTime()).createTime(e.getCreateTime())
                .build();
    }
}
