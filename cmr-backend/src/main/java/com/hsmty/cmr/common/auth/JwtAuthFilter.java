package com.hsmty.cmr.common.auth;

import com.hsmty.cmr.config.CmrProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器：解析 Bearer token → 填充 SecurityContext 与 AuthContext。
 * cmr.auth.enabled=false 时临时注入系统管理员，保留后续一键恢复鉴权能力。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final LoginUser SYSTEM_USER = new LoginUser(0L, "admin", "管理员", "admin");

    private final JwtService jwtService;
    private final CmrProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.getAuth().isEnabled()) {
            populateContext(SYSTEM_USER);
        } else {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                LoginUser user = jwtService.verify(header.substring(7));
                if (user != null) {
                    populateContext(user);
                }
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void populateContext(LoginUser user) {
        List<SimpleGrantedAuthority> authorities = Permissions.of(user.role()).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.username(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        AuthContext.set(user);
    }
}
