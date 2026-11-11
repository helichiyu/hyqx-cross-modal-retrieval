package com.hsmty.cmr.domain.dataset.vo;

import com.hsmty.cmr.domain.dataset.entity.CmrDatasource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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

    /**
     * 缺失的必填项，取值 dataset_id/name/item_count/modals。
     *
     * <p>requiredOk=true 时为空数组；尚未同步/测试过、无法判定时为 null（未知）。
     */
    @Schema(description = "缺失的必填项：dataset_id/name/item_count/modals；空数组=齐全，null=未知")
    private List<String> missingFields;

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

    /**
     * 实体转视图。
     *
     * @param e 数据源实体
     * @return 数据源视图
     */
    public static DatasourceVO from(CmrDatasource e) {
        return DatasourceVO.builder()
                .id(e.getId()).bizNo(e.getBizNo()).name(e.getName())
                .endpoint(e.getEndpoint()).protocol(e.getProtocol()).authType(e.getAuthType())
                .remark(e.getRemark()).datasetCount(e.getDatasetCount())
                .connectStatus(e.getConnectStatus() == null ? "unconnected" : e.getConnectStatus())
                .requiredOk(Boolean.TRUE.equals(e.getRequiredOk()))
                .missingFields(missingFieldsOf(e))
                .upstreamDatasetId(e.getUpstreamDatasetId())
                .vectorizedCount(e.getVectorizedCount() == null ? 0 : e.getVectorizedCount())
                .totalCount(e.getTotalCount() == null ? 0 : e.getTotalCount())
                .lastSyncTime(e.getLastSyncTime())
                .lastTestConnected(e.getLastTestConnected()).lastTestStatusCode(e.getLastTestStatusCode())
                .lastTestLatencyMs(e.getLastTestLatencyMs()).lastTestMessage(e.getLastTestMessage())
                .lastTestTime(e.getLastTestTime()).createTime(e.getCreateTime())
                .build();
    }

    /**
     * 解析落库的缺失项：齐全时返回空集合，未判定时返回 null。
     *
     * @param e 数据源实体
     * @return 缺失必填项列表
     */
    private static List<String> missingFieldsOf(CmrDatasource e) {
        String raw = e.getMissingFields();
        if (raw == null || raw.isBlank()) {
            return Boolean.TRUE.equals(e.getRequiredOk()) ? List.of() : null;
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(field -> !field.isEmpty())
                .toList();
    }
}
