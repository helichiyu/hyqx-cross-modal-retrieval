package com.hsmty.cmr.domain.dataset.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源黑名单：按来源名匹配整源排除，可随时移出（数据流 3.1）
 *
 * @author cmr
 * @since 2026-11-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_datasource_blacklist", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrDatasourceBlacklist extends BaseEntity {

    private String datasetId;

    private String name;

    /** 来源名，结果页按此匹配排除 */
    private String sourceName;

    private String endpoint;
}
