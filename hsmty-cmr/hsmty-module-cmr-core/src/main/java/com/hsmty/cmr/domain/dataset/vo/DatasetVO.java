package com.hsmty.cmr.domain.dataset.vo;

import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据集视图（P4 列表）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class DatasetVO {

    private Long id;

    private String bizNo;

    private Long datasourceId;

    private String upstreamDatasetId;

    private String name;

    private String version;

    private String scene;

    private String status;

    private Integer itemCount;

    private String modalityJson;

    private Long currentIndexId;

    private LocalDateTime createTime;

    public static DatasetVO from(CmrDataset e) {
        return DatasetVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).datasourceId(e.getDatasourceId())
                .upstreamDatasetId(e.getUpstreamDatasetId())
                .name(e.getName()).version(e.getVersion()).scene(e.getScene())
                .status(e.getStatus())
                .itemCount(e.getItemCount()).modalityJson(e.getModalityJson())
                .currentIndexId(e.getCurrentIndexId()).createTime(e.getCreateTime())
                .build();
    }
}
