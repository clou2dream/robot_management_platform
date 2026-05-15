package com.robotmanagement.common.security;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class AuthTokenStore {

    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    public AuthTokenStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeRefreshSession(String tokenHash, RefreshSession session, Duration ttl) {
        redisTemplate.opsForValue().set(REFRESH_PREFIX + tokenHash, session, ttl);
    }

    public Optional<RefreshSession> getRefreshSession(String tokenHash) {
        Object value = redisTemplate.opsForValue().get(REFRESH_PREFIX + tokenHash);
        if (value instanceof RefreshSession session) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    public void deleteRefreshSession(String tokenHash) {
        redisTemplate.delete(REFRESH_PREFIX + tokenHash);
    }

    public void blacklistAccessToken(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
