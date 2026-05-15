package com.robotmanagement.alert.dto;

import com.robotmanagement.alert.entity.AlertEntity;
import com.robotmanagement.robot.entity.RobotEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AlertResponse(
    UUID id,
    UUID robotId,
    String robotSn,
    String manufacturer,
    String status,
    String level,
    String errorType,
    String description,
    String hint,
    Map<String, Object> rawError,
    OffsetDateTime triggeredAt,
    OffsetDateTime resolvedAt
) {

    public static AlertResponse from(AlertEntity entity, RobotEntity robot) {
        return new AlertResponse(
            entity.getId(),
            entity.getRobotId(),
            robot == null ? null : robot.getSerialNumber(),
            robot == null ? null : robot.getManufacturer(),
            entity.getResolvedAt() == null ? "open" : "resolved",
            entity.getLevel(),
            entity.getErrorType(),
            entity.getDescription(),
            entity.getHint(),
            entity.getRawError(),
            entity.getTriggeredAt(),
            entity.getResolvedAt()
        );
    }
}
