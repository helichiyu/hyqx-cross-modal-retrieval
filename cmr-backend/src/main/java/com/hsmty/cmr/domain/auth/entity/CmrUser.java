package com.hsmty.cmr.domain.auth.entity;

import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.config.AuditListener;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "cmr_user", onInsert = AuditListener.class, onUpdate = AuditListener.class)
public class CmrUser extends BaseEntity {

    private String username;

    /** BCrypt */
    private String password;

    private String nickname;

    /** admin/operator/user */
    private String role;

    /** 1 启用 0 停用 */
    private String status;
}
