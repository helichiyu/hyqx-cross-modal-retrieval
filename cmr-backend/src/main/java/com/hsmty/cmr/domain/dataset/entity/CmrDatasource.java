package com.hsmty.cmr.domain.dataset.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
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

    /** http/api/upload */
    private String protocol;

    /** none/token/basic */
    private String authType;

    /** AES 加密存储 */
    private String authConfig;

    private String remark;
}
