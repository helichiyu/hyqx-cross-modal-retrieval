package com.hsmty.cmr.domain.dataset.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;

/**
 * 数据源（SRC）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_datasource", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrDatasource extends BaseEntity {

    private String bizNo;

    private String name;

    private String endpoint;

    /** pull/http/api=主动拉取；push/upload=推送接收 */
    private String protocol;

    /** none/token/basic */
    private String authType;

    /** AES 加密存储 */
    private String authConfig;

    /** unconnected/connected/failed */
    private String connectStatus;

    /** 上游 4 项必填是否齐全 */
    private Boolean requiredOk;

    /** 缺失的必填项，逗号分隔（dataset_id/name/item_count/modals）；null=未判定 */
    @Schema(description = "缺失的必填项，逗号分隔：dataset_id/name/item_count/modals")
    private String missingFields;

    /** 上游 dataset_id */
    private String upstreamDatasetId;

    private Integer vectorizedCount;

    private Integer totalCount;

    private LocalDateTime lastSyncTime;

    private Boolean lastTestConnected;

    private Integer lastTestStatusCode;

    private Long lastTestLatencyMs;

    private String lastTestMessage;

    private LocalDateTime lastTestTime;

    private String remark;

    /** 非表字段：该数据源下数据集总数，由服务回填 */
    @com.mybatisflex.annotation.Column(ignore = true)
    private Long datasetCount;
}
