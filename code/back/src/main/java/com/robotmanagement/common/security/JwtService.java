package com.robotmanagement.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(CurrentUser currentUser) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
            .issuer(properties.issuer())
            .subject(currentUser.operatorId().toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim("tenant_id", currentUser.tenantId().toString())
            .claim("username", currentUser.username())
            .claim("role", currentUser.role())
            .claim("tenant_name", currentUser.tenantName())
            .claim("debug_permission", currentUser.debugPermission())
            .signWith(secretKey)
            .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(properties.issuer())
            .build()
            .parseSignedClaims(token);
    }

    public CurrentUser toCurrentUser(Claims claims) {
        return new CurrentUser(
            UUID.fromString(claims.getSubject()),
            UUID.fromString(claims.get("tenant_id", String.class)),
            claims.get("username", String.class),
            claims.get("role", String.class),
            claims.get("tenant_name", String.class),
            Boolean.TRUE.equals(claims.get("debug_permission", Boolean.class))
        );
    }

    public Duration remainingTtl(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), expiration.toInstant());
    }

    public Duration refreshTokenTtl() {
        return properties.refreshTokenTtl();
    }
}
