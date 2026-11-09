package com.hsmty.cmr.domain.dataset.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
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
