package com.robotmanagement.robot.dto;

import com.robotmanagement.robot.entity.RobotEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RobotResponse(
    UUID id,
    String externalRobotId,
    String serialNumber,
    String manufacturer,
    String sourceAppId,
    String status,
    boolean online,
    OffsetDateTime syncedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static RobotResponse from(RobotEntity entity, boolean online) {
        return new RobotResponse(
            entity.getId(),
            entity.getExternalRobotId(),
            entity.getSerialNumber(),
            entity.getManufacturer(),
            entity.getSourceAppId(),
            entity.getStatus(),
            online,
            entity.getSyncedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
