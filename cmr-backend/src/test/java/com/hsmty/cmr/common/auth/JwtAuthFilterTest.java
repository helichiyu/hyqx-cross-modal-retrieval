package com.hsmty.cmr.common.auth;

import com.hsmty.cmr.config.CmrProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CmrProperties properties = new CmrProperties();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        AuthContext.clear();
    }

    @Test
    void disabledAuthInjectsSystemAdmin() throws Exception {
        properties.getAuth().setEnabled(false);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, properties);
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        AtomicReference<LoginUser> user = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            authentication.set(SecurityContextHolder.getContext().getAuthentication());
            user.set(AuthContext.get());
        });

        assertThat(authentication.get()).isNotNull();
        assertThat(authentication.get().getName()).isEqualTo("admin");
        assertThat(authentication.get().getAuthorities()).extracting("authority").contains("cmr:dataset:page");
        assertThat(user.get()).isEqualTo(new LoginUser(0L, "admin", "管理员", "admin"));
    }

    @Test
    void enabledAuthWithoutTokenLeavesContextEmpty() throws Exception {
        properties.getAuth().setEnabled(true);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, properties);
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        AtomicReference<LoginUser> user = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            authentication.set(SecurityContextHolder.getContext().getAuthentication());
            user.set(AuthContext.get());
        });

        assertThat(authentication.get()).isNull();
        assertThat(user.get()).isNull();
    }

    @Test
    void enabledAuthParsesBearerToken() throws Exception {
        properties.getAuth().setEnabled(true);
        LoginUser loginUser = new LoginUser(9L, "operator", "运营人员", "operator");
        when(jwtService.verify("token-123")).thenReturn(loginUser);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-123");
        AtomicReference<LoginUser> user = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> user.set(AuthContext.get()));

        verify(jwtService).verify("token-123");
        assertThat(user.get()).isEqualTo(loginUser);
    }
}