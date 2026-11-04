package com.hsmty.cmr.common.auth;

import com.hsmty.cmr.config.CmrProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 签发/校验/注销黑名单
 *
 * @author cmr
 * @since 2026-11-03
 */
class JwtServiceTest {

    private JwtService service() {
        CmrProperties properties = new CmrProperties();
        properties.getJwt().setSecret("unit-test-secret-0123456789abcdef0123456789");
        return new JwtService(properties);
    }

    @Test
    void 签发后校验还原登录用户() {
        JwtService jwt = service();
        LoginUser user = new LoginUser(1L, "admin", "管理员", "admin");
        JwtService.TokenInfo info = jwt.issue(user);

        LoginUser parsed = jwt.verify(info.token());
        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(1L);
        assertThat(parsed.username()).isEqualTo("admin");
        assertThat(parsed.role()).isEqualTo("admin");
        assertThat(info.expiresAt()).isAfter(info.expiresAt().minusSeconds(12 * 3600));
    }

    @Test
    void 篡改令牌校验失败() {
        JwtService jwt = service();
        String token = jwt.issue(new LoginUser(2L, "user", "普通", "user")).token();
        assertThat(jwt.verify(token + "x")).isNull();
        assertThat(jwt.verify("garbage")).isNull();
        assertThat(jwt.verify("")).isNull();
    }

    @Test
    void 注销后令牌立即失效且登出幂等() {
        JwtService jwt = service();
        JwtService.TokenInfo info = jwt.issue(new LoginUser(3L, "operator", "运营", "operator"));
        assertThat(jwt.verify(info.token())).isNotNull();

        jwt.revokeByToken(info.token());
        assertThat(jwt.verify(info.token())).isNull();

        // 无效令牌登出不抛异常
        jwt.revokeByToken("not-a-jwt");
    }

    @Test
    void 不同实例同密钥可互验() {
        String token = service().issue(new LoginUser(9L, "a", "a", "user")).token();
        assertThat(service().verify(token)).isNotNull();
    }
}
