package com.hsmty.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地联调专用身份注入：在没有 UAP 门户会话的情况下为请求补一个固定操作人。
 *
 * <p>仅 {@code local} profile 生效。过滤器注册在平台安全链之后、业务控制器之前，
 * 因此仍复用平台的安全链与白名单配置，只在方法级鉴权前补身份；
 * 非 local 环境本类不会加载。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Configuration
@Profile("local")
public class LocalDevSecurityConfiguration {

    private static final List<String> LOCAL_AUTHORITIES = List.of(
            "cmr:file:upload",
            "cmr:file:page",
            "cmr:file:detail",
            "cmr:file:remove",
            "cmr:file:read",
            "cmr:search:create");

    @Bean
    public FilterRegistrationBean<LocalDevAuthenticationFilter> localDevAuthenticationFilter() {
        FilterRegistrationBean<LocalDevAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new LocalDevAuthenticationFilter());
        registration.addUrlPatterns("/*");
        // 排在平台安全链（-100）之后，保证 SecurityContext 已建立，控制器方法鉴权前生效
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }

    /**
     * 为每个本地请求注入固定的操作人身份与全部业务权限。
     */
    static class LocalDevAuthenticationFilter extends OncePerRequestFilter {

        private static final Logger LOG = LoggerFactory.getLogger(LocalDevSecurityConfiguration.class);
        private static final AtomicBoolean LOGGED = new AtomicBoolean();

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (LOGGED.compareAndSet(false, true)) {
                LOG.info("Local dev auth filter active: {} {}", request.getMethod(), request.getRequestURI());
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "local-dev", "n/a",
                        LOCAL_AUTHORITIES.stream().map(SimpleGrantedAuthority::new).toList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        }
    }
}
