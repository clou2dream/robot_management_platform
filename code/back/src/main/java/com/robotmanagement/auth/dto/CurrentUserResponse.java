package com.robotmanagement.auth.dto;

import com.robotmanagement.common.security.CurrentUser;

import java.util.UUID;

public record CurrentUserResponse(
    UUID operatorId,
    UUID tenantId,
    String username,
    String role,
    String tenantName,
    boolean debugPermission
) {

    public static CurrentUserResponse from(CurrentUser currentUser) {
        return new CurrentUserResponse(
            currentUser.operatorId(),
            currentUser.tenantId(),
            currentUser.username(),
            currentUser.role(),
            currentUser.tenantName(),
            currentUser.debugPermission()
        );
    }
}
