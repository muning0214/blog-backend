package com.devlog.common.jwt;

import com.devlog.config.DevLogProperties;
import com.devlog.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验。
 *
 * <p>密钥不再硬编码在配置里带着默认值 —— 缺失或长度不足 32 字节时直接启动失败，
 * 而不是悄悄用一个弱密钥跑起来。原 test1 项目把密钥连同 base64 解码后的明文注释
 * 一起提交进了仓库，等于认证体系形同虚设。
 */
@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(DevLogProperties properties) {
        String secret = properties.jwt() == null ? null : properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "缺少 devlog.jwt.secret。本地开发请写在 application-local.yml，"
                            + "线上用 JWT_SECRET 环境变量注入。");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "devlog.jwt.secret 太短：HS256 至少需要 " + MIN_SECRET_BYTES + " 字节，当前 " + bytes.length + " 字节。");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = properties.jwt().expirationMsOrDefault();
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("nickname", user.getNickname())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验签名与有效期。
     *
     * @throws io.jsonwebtoken.ExpiredJwtException 令牌已过期
     * @throws io.jsonwebtoken.JwtException         签名不合法或格式错误
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
