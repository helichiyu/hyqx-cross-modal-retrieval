package com.hsmty.cmr.common.auth;

/**
 * 当前登录用户 ThreadLocal（由 JwtAuthFilter 填充/清理；审计字段填充读取此上下文）
 *
 * @author cmr
 * @since 2026-11-03
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
