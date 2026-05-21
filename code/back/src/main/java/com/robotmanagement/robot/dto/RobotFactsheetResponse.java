package com.robotmanagement.robot.dto;

import com.robotmanagement.robot.entity.RobotFactsheetEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record RobotFactsheetResponse(
    UUID robotId,
    OffsetDateTime receivedAt,
    Map<String, Object> rawPayload
) {

    public static RobotFactsheetResponse from(RobotFactsheetEntity entity) {
        return from(entity, true);
    }

    public static RobotFactsheetResponse from(RobotFactsheetEntity entity, boolean includeRawPayload) {
        if (entity == null) {
            return null;
        }
        return new RobotFactsheetResponse(
            entity.getRobotId(),
            entity.getReceivedAt(),
            includeRawPayload ? entity.getRawPayload() : null
        );
    }
}
