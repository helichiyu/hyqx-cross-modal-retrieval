package com.hsmty.cmr.common.auth;

import com.hsmty.cmr.config.CmrProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT 签发/校验（HS256，默认 12h）+ 登出黑名单（单机内存版）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final CmrProperties properties;

    /** jti → 过期时间，懒清理 */
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public record TokenInfo(String token, String jti, Instant expiresAt) {
    }

    public TokenInfo issue(LoginUser user) {
        Instant exp = Instant.now().plusSeconds(properties.getJwt().getExpireHours() * 3600L);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(user.username())
                .claims(Map.of(
                        "uid", user.userId(),
                        "nickname", user.nickname() == null ? "" : user.nickname(),
                        "role", user.role()))
                .expiration(Date.from(exp))
                .signWith(key())
                .compact();
        return new TokenInfo(token, jti, exp);
    }

    /** 校验并解析；无效/过期/已注销返回 null */
    public LoginUser verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            String jti = claims.getId();
            if (jti != null && revoked.containsKey(jti)) {
                return null;
            }
            Object uid = claims.get("uid");
            return new LoginUser(
                    uid == null ? null : Long.valueOf(uid.toString()),
                    claims.getSubject(),
                    String.valueOf(claims.get("nickname")),
                    String.valueOf(claims.get("role")));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return null;
        }
    }

    public void revoke(String jti, Instant expiresAt) {
        if (jti == null) {
            return;
        }
        revoked.put(jti, expiresAt == null ? Instant.now().plusSeconds(3600) : expiresAt);
        revoked.entrySet().removeIf(e -> e.getValue().isBefore(Instant.now()));
    }

    /** 按原始令牌登出（解析 jti/过期时间后加黑名单；无效令牌幂等忽略） */
    public void revokeByToken(String rawToken) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(rawToken).getPayload();
            revoke(claims.getId(),
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ignored) {
            // 无效令牌登出幂等处理
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }
}

