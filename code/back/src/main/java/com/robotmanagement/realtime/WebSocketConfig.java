package com.robotmanagement.realtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.common.security.AuthTokenStore;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.JwtService;
import com.robotmanagement.common.security.PlatformSecurityProperties;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final PlatformSecurityProperties securityProperties;
    private final JwtService jwtService;
    private final AuthTokenStore authTokenStore;
    private final RobotMapper robotMapper;
    private final OperatorRobotAccessMapper accessMapper;

    public WebSocketConfig(
        PlatformSecurityProperties securityProperties,
        JwtService jwtService,
        AuthTokenStore authTokenStore,
        RobotMapper robotMapper,
        OperatorRobotAccessMapper accessMapper
    ) {
        this.securityProperties = securityProperties;
        this.jwtService = jwtService;
        this.authTokenStore = authTokenStore;
        this.robotMapper = robotMapper;
        this.accessMapper = accessMapper;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(parseOrigins());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscribe(accessor);
                }
                return message;
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = bearerToken(accessor.getFirstNativeHeader("Authorization"));
        if (!StringUtils.hasText(token)) {
            throw new AccessDeniedException("WebSocket token is missing");
        }

        Jws<Claims> parsed = jwtService.parse(token);
        Claims claims = parsed.getPayload();
        if (authTokenStore.isBlacklisted(claims.getId())) {
            throw new AccessDeniedException("WebSocket token has been revoked");
        }

        CurrentUser currentUser = jwtService.toCurrentUser(claims);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            currentUser,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role().toUpperCase()))
        );
        accessor.setUser(authentication);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
            || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new AccessDeniedException("WebSocket subscription requires authentication");
        }

        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            return;
        }
        if (destination.equals("/topic/alerts")) {
            return;
        }
        if (destination.startsWith("/topic/robots/")) {
            UUID robotId = parseRobotId(destination);
            RobotEntity robot = robotId == null ? null : robotMapper.selectById(robotId);
            if (robot == null || !currentUser.tenantId().equals(robot.getTenantId())) {
                throw new AccessDeniedException("Robot subscription is not allowed");
            }
            if (currentUser.isAdmin()) {
                return;
            }
            Long count = accessMapper.selectCount(
                new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                    .eq(OperatorRobotAccessEntity::getOperatorId, currentUser.operatorId())
                    .eq(OperatorRobotAccessEntity::getRobotId, robotId)
            );
            if (count == null || count == 0) {
                throw new AccessDeniedException("Robot subscription is not allowed");
            }
            return;
        }

        throw new AccessDeniedException("Unsupported WebSocket subscription");
    }

    private UUID parseRobotId(String destination) {
        String[] parts = destination.split("/");
        if (parts.length < 4) {
            return null;
        }
        try {
            return UUID.fromString(parts[3]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String bearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }

    private String[] parseOrigins() {
        String allowedOrigins = securityProperties.allowedOrigins();
        if (!StringUtils.hasText(allowedOrigins)) {
            return new String[] {"*"};
        }
        return Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toArray(String[]::new);
    }
}
