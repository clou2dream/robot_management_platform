package com.robotmanagement.common.security;

import com.robotmanagement.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw BusinessException.unauthorized("未登录或登录已过期");
        }
        return currentUser;
    }

    public static UUID currentTenantId() {
        return currentUser().tenantId();
    }

    public static UUID currentOperatorId() {
        return currentUser().operatorId();
    }
}
