package com.hsmty.cmr.common.auth;

import java.util.List;
import java.util.Set;

/**
 * 角色 → 权限码映射（docs/前后端方案/04 第一节，@PreAuthorize 与此处保持一致）
 *
 * @author cmr
 * @since 2026-11-03
 */
public final class Permissions {

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_OPERATOR = "operator";
    public static final String ROLE_USER = "user";

    /** 全量权限码（admin 拥有） */
    public static final List<String> ALL = List.of(
            "cmr:dataset:page", "cmr:dataset:detail", "cmr:dataset:create", "cmr:dataset:update",
            "cmr:dataset:adjudicate", "cmr:dataset:confirm",
            "cmr:search:create", "cmr:search:page", "cmr:search:adopt",
            "cmr:task:page", "cmr:task:detail",
            "cmr:strategy:page", "cmr:strategy:detail", "cmr:strategy:create", "cmr:strategy:update", "cmr:strategy:publish",
            "cmr:eval:page", "cmr:eval:detail", "cmr:eval:create", "cmr:eval:update", "cmr:eval:run",
            "cmr:feedback:create", "cmr:feedback:page", "cmr:feedback:update",
            "cmr:word:page", "cmr:word:create", "cmr:word:update", "cmr:word:remove",
            "cmr:file:read", "cmr:file:upload", "cmr:file:page", "cmr:file:detail", "cmr:file:remove");

    private static final Set<String> OPERATOR = Set.of(
            "cmr:search:create", "cmr:search:page", "cmr:search:adopt",
            "cmr:task:page", "cmr:task:detail",
            "cmr:strategy:page", "cmr:strategy:detail", "cmr:strategy:create", "cmr:strategy:update", "cmr:strategy:publish",
            "cmr:eval:page", "cmr:eval:detail", "cmr:eval:create", "cmr:eval:update", "cmr:eval:run",
            "cmr:feedback:create", "cmr:feedback:page", "cmr:feedback:update",
            "cmr:word:page", "cmr:word:create", "cmr:word:update", "cmr:word:remove",
            "cmr:file:read");

    private static final Set<String> USER = Set.of(
            "cmr:search:create", "cmr:search:page", "cmr:search:adopt",
            "cmr:task:page", "cmr:task:detail",
            // P3 结果摘要「标记不匹配」由检索用户发起，需可登记反馈
            "cmr:feedback:create",
            "cmr:file:read");

    private Permissions() {
    }

    /** 角色对应的权限码列表；未知角色按最低权限处理 */
    public static List<String> of(String role) {
        if (ROLE_ADMIN.equals(role)) {
            return ALL;
        }
        if (ROLE_OPERATOR.equals(role)) {
            return List.copyOf(OPERATOR);
        }
        return List.copyOf(USER);
    }

    public static boolean isValidRole(String role) {
        return ROLE_ADMIN.equals(role) || ROLE_OPERATOR.equals(role) || ROLE_USER.equals(role);
    }
}
