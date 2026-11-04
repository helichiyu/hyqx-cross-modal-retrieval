package com.hsmty.cmr.common.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：雪花主键 + 审计字段 + 逻辑删除（值由 {@link com.hsmty.cmr.config.AuditListener} 填充，业务代码不手写）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.None)
    private Long id;

    private Long createId;

    private LocalDateTime createTime;

    private Long updateId;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private String delFlag;
}
