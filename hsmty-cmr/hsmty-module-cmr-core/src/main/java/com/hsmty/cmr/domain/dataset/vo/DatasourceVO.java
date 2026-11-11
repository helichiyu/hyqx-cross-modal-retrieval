package com.hsmty.cmr.domain.dataset.vo;

import com.hsmty.cmr.domain.dataset.entity.CmrDatasource;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源视图（认证信息不回显原文）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class DatasourceVO {

    private Long id;

    private String bizNo;

    private String name;

    private String endpoint;

    private String protocol;

    private String authType;

    private String remark;

    private Long datasetCount;

    /** unconnected/connected/failed */
    private String connectStatus;

    /** 上游 4 项必填是否齐全 */
    private Boolean requiredOk;

    private String upstreamDatasetId;

    private Integer vectorizedCount;

    private Integer totalCount;

    private LocalDateTime lastSyncTime;

    private Boolean lastTestConnected;

    private Integer lastTestStatusCode;

    private Long lastTestLatencyMs;

    private String lastTestMessage;

    private LocalDateTime lastTestTime;

    private LocalDateTime createTime;

    public static DatasourceVO from(CmrDatasource e) {
        return DatasourceVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).name(e.getName())
                .endpoint(e.getEndpoint()).protocol(e.getProtocol()).authType(e.getAuthType())
                .remark(e.getRemark()).datasetCount(e.getDatasetCount())
                .connectStatus(e.getConnectStatus() == null ? "unconnected" : e.getConnectStatus())
                .requiredOk(Boolean.TRUE.equals(e.getRequiredOk()))
                .upstreamDatasetId(e.getUpstreamDatasetId())
                .vectorizedCount(e.getVectorizedCount() == null ? 0 : e.getVectorizedCount())
                .totalCount(e.getTotalCount() == null ? 0 : e.getTotalCount())
                .lastSyncTime(e.getLastSyncTime())
                .lastTestConnected(e.getLastTestConnected()).lastTestStatusCode(e.getLastTestStatusCode())
                .lastTestLatencyMs(e.getLastTestLatencyMs()).lastTestMessage(e.getLastTestMessage())
                .lastTestTime(e.getLastTestTime()).createTime(e.getCreateTime())
                .build();
    }
}
