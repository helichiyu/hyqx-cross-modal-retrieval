package com.hsmty.cmr.common.auth;

/**
 * 登录用户上下文对象
 *
 * @param userId   用户主键（内部）
 * @param username 登录名
 * @param nickname 显示名
 * @param role     admin/operator/user
 * @author cmr
 * @since 2026-11-03
 */
public record LoginUser(Long userId, String username, String nickname, String role) {
}
