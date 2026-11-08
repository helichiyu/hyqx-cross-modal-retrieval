package com.hsmty.cmr.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器：解析 Bearer token → 填充 SecurityContext 与 AuthContext
 *
 * @author cmr
 * @since 2026-11-03
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /* 临时关闭登录鉴权：所有请求注入管理员上下文。 */
    private static final LoginUser BYPASS_USER = new LoginUser(0L, "admin", "管理员", "admin");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<SimpleGrantedAuthority> authorities = Permissions.of(BYPASS_USER.role()).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                BYPASS_USER.username(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        AuthContext.set(BYPASS_USER);
        try {
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
