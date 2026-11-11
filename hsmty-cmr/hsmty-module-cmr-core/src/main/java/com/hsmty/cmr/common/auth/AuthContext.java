package com.hsmty.cmr.common.auth;

import com.hsmty.core.common.entity.BaseUserInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户上下文。
 *
 * <p>从平台安全上下文读取，兼容两种 principal：平台 {@code LoginUser}
 * 与本机联调使用的字符串身份。旧版本基于 JWT + ThreadLocal，迁移到 HSMTY
 * 后身份统一由 UAP 安全链注入，这里只做只读适配。
 *
 * @author cmr
 * @since 2026-11-03
 */
public final class AuthContext {

    private AuthContext() {
    }

    /**
     * 当前登录用户；未登录返回 null。
     *
     * @return 用户上下文
     */
    public static LoginUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.hsmty.core.common.entity.LoginUser loginUser) {
            BaseUserInfo info = loginUser.getUserInfo();
            Long userId = info == null ? null : info.getId();
            String username = info == null ? authentication.getName() : info.getUsername();
            String nickname = info == null ? null : info.getNickname();
            return new LoginUser(userId, username, nickname, firstRole(loginUser.getRoles()));
        }
        if (principal instanceof String username && !"anonymousUser".equals(username)) {
            return new LoginUser(0L, username, username, Permissions.ROLE_USER);
        }
        return null;
    }

    /**
     * 当前用户主键；未登录返回 null。
     *
     * @return 用户主键
     */
    public static Long userId() {
        LoginUser user = get();
        return user == null ? null : user.userId();
    }

    /**
     * 当前用户权限码。
     *
     * @return 权限码列表
     */
    public static List<String> permissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        List<String> permissions = new ArrayList<>();
        authentication.getAuthorities().forEach(authority -> permissions.add(authority.getAuthority()));
        return permissions;
    }

    private static String firstRole(java.util.Set<String> roles) {
        return roles == null || roles.isEmpty() ? Permissions.ROLE_USER : roles.iterator().next();
    }
}
