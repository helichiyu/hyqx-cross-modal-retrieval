package com.hsmty.cmr.common.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：雪花主键 + 审计字段 + 逻辑删除。
 *
 * <p>审计字段与逻辑删除初值由 {@link com.hsmty.cmr.config.AuditListener} 填充；
 * {@code id} 需业务代码在插入前显式赋值（{@code setId(idWorker.next())}）。
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
