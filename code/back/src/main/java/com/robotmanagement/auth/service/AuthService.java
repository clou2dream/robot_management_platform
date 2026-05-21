package com.robotmanagement.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.auth.dto.CurrentUserResponse;
import com.robotmanagement.auth.dto.LoginRequest;
import com.robotmanagement.auth.dto.LoginResponse;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.AuthTokenStore;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.JwtService;
import com.robotmanagement.common.security.PlatformSecurityProperties;
import com.robotmanagement.common.security.RefreshSession;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.common.util.HashUtils;
import com.robotmanagement.operator.entity.OperatorEntity;
import com.robotmanagement.operator.mapper.OperatorMapper;
import com.robotmanagement.tenant.entity.TenantEntity;
import com.robotmanagement.tenant.mapper.TenantMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final Duration LOGIN_FAIL_TTL = Duration.ofMinutes(15);
    private static final int MAX_LOGIN_FAILS = 5;

    private final OperatorMapper operatorMapper;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenStore authTokenStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final PlatformSecurityProperties securityProperties;

    public AuthService(
        OperatorMapper operatorMapper,
        TenantMapper tenantMapper,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuthTokenStore authTokenStore,
        StringRedisTemplate stringRedisTemplate,
        PlatformSecurityProperties securityProperties
    ) {
        this.operatorMapper = operatorMapper;
        this.tenantMapper = tenantMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authTokenStore = authTokenStore;
        this.stringRedisTemplate = stringRedisTemplate;
        this.securityProperties = securityProperties;
    }

    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        String username = request.username().trim();
        assertNotLocked(username);

        OperatorEntity operator = operatorMapper.selectOne(
            new LambdaQueryWrapper<OperatorEntity>().eq(OperatorEntity::getUsername, username)
        );

        if (operator == null || !passwordEncoder.matches(request.password(), operator.getPasswordHash())) {
            recordLoginFailure(username);
            throw BusinessException.unauthorized("账号或密码错误");
        }

        clearLoginFailure(username);
        CurrentUser currentUser = buildCurrentUser(operator);
        String accessToken = jwtService.createAccessToken(currentUser);
        issueRefreshCookie(response, currentUser);
        return new LoginResponse(accessToken, CurrentUserResponse.from(currentUser));
    }

    public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        verifyOriginOrReferer(request);
        String refreshToken = readRefreshToken(request)
            .orElseThrow(() -> BusinessException.unauthorized("Refresh Token 不存在"));

        String tokenHash = HashUtils.sha256Hex(refreshToken);
        RefreshSession session = authTokenStore.getRefreshSession(tokenHash)
            .orElseThrow(() -> BusinessException.unauthorized("Refresh Token 已失效"));

        OperatorEntity operator = operatorMapper.selectById(session.operatorId());
        if (operator == null) {
            authTokenStore.deleteRefreshSession(tokenHash);
            throw BusinessException.unauthorized("账号不存在");
        }

        CurrentUser currentUser = buildCurrentUser(operator);
        authTokenStore.storeRefreshSession(tokenHash, session, jwtService.refreshTokenTtl());
        writeRefreshCookie(response, refreshToken, jwtService.refreshTokenTtl());
        String accessToken = jwtService.createAccessToken(currentUser);
        return new LoginResponse(accessToken, CurrentUserResponse.from(currentUser));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        verifyOriginOrReferer(request);
        extractBearerToken(request).ifPresent(token -> {
            try {
                Claims claims = jwtService.parse(token).getPayload();
                authTokenStore.blacklistAccessToken(claims.getId(), jwtService.remainingTtl(claims));
            } catch (JwtException | IllegalArgumentException ignored) {
                // Logout should be idempotent even when the access token is already invalid.
            }
        });

        readRefreshToken(request)
            .map(HashUtils::sha256Hex)
            .ifPresent(authTokenStore::deleteRefreshSession);

        clearRefreshCookie(response);
    }

    public CurrentUserResponse me() {
        CurrentUser principal = SecurityUtils.currentUser();
        OperatorEntity operator = operatorMapper.selectById(principal.operatorId());
        if (operator == null) {
            return CurrentUserResponse.from(principal);
        }
        return CurrentUserResponse.from(buildCurrentUser(operator));
    }

    private CurrentUser buildCurrentUser(OperatorEntity operator) {
        TenantEntity tenant = tenantMapper.selectById(operator.getTenantId());
        String tenantName = tenant == null ? null : tenant.getName();
        return new CurrentUser(
            operator.getId(),
            operator.getTenantId(),
            operator.getUsername(),
            operator.getRole(),
            tenantName,
            Boolean.TRUE.equals(operator.getDebugPermission())
        );
    }

    private void issueRefreshCookie(HttpServletResponse response, CurrentUser currentUser) {
        String refreshToken = UUID.randomUUID().toString();
        String tokenHash = HashUtils.sha256Hex(refreshToken);
        RefreshSession session = new RefreshSession(
            UUID.randomUUID(),
            currentUser.operatorId(),
            currentUser.tenantId(),
            OffsetDateTime.now()
        );
        authTokenStore.storeRefreshSession(tokenHash, session, jwtService.refreshTokenTtl());
        writeRefreshCookie(response, refreshToken, jwtService.refreshTokenTtl());
    }

    private void writeRefreshCookie(HttpServletResponse response, String token, Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
            .httpOnly(true)
            .secure(securityProperties.cookieSecure())
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(ttl)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(securityProperties.cookieSecure())
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Optional<String> readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> REFRESH_COOKIE.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(StringUtils::hasText)
            .findFirst();
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return Optional.of(authorization.substring(7));
    }

    private void verifyOriginOrReferer(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String referer = request.getHeader(HttpHeaders.REFERER);

        if (!StringUtils.hasText(origin) && !StringUtils.hasText(referer)) {
            return;
        }

        String allowedOrigins = securityProperties.allowedOrigins();
        boolean allowed = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .anyMatch(allowedOrigin ->
                allowedOrigin.equals(origin)
                    || (StringUtils.hasText(referer) && referer.startsWith(allowedOrigin))
            );

        if (!allowed) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "非法跨站请求");
        }
    }

    private void assertNotLocked(String username) {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(username)))) {
            throw new BusinessException(HttpStatus.LOCKED, "账号已锁定，请稍后重试");
        }
    }

    private void recordLoginFailure(String username) {
        Long count = stringRedisTemplate.opsForValue().increment(failCountKey(username));
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failCountKey(username), LOGIN_FAIL_TTL);
        }
        if (count != null && count >= MAX_LOGIN_FAILS) {
            stringRedisTemplate.opsForValue().set(lockKey(username), "1", LOGIN_FAIL_TTL);
        }
    }

    private void clearLoginFailure(String username) {
        stringRedisTemplate.delete(failCountKey(username));
        stringRedisTemplate.delete(lockKey(username));
    }

    private String failCountKey(String username) {
        return "auth:fail_count:" + username;
    }

    private String lockKey(String username) {
        return "auth:lock:" + username;
    }
}
