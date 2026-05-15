package com.robotmanagement.operator.dto;

import com.robotmanagement.operator.entity.OperatorEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OperatorResponse(
    UUID id,
    UUID tenantId,
    String username,
    String role,
    long robotAccessCount,
    boolean passwordResetRequired,
    OffsetDateTime createdAt
) {

    public static OperatorResponse from(OperatorEntity entity, long robotAccessCount) {
        return new OperatorResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getUsername(),
            entity.getRole(),
            robotAccessCount,
            Boolean.TRUE.equals(entity.getPasswordResetRequired()),
            entity.getCreatedAt()
        );
    }
}
