package com.robotmanagement.common.security;

import java.util.UUID;

public record CurrentUser(
    UUID operatorId,
    UUID tenantId,
    String username,
    String role,
    String tenantName,
    boolean debugPermission
) {

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isOperator() {
        return "operator".equals(role);
    }

    public boolean isViewer() {
        return "viewer".equals(role);
    }
}
