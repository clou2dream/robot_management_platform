package com.robotmanagement.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record PlatformSecurityProperties(
    String allowedOrigins,
    boolean cookieSecure
) {
}
