package com.hsmty.cmr.domain.auth.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典项（fallback 单表：type=类型 alias，scene 等可维护，其余枚举类）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_dict", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrDict extends BaseEntity {

    private String type;

    private String alias;

    private String label;

    private Integer sort;

    private Boolean enabled;

    private String remark;
}
