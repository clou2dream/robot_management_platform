package com.robotmanagement.robot.dto;

import com.robotmanagement.robot.entity.RobotConnectionEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RobotConnectionResponse(
    UUID robotId,
    String connectionState,
    boolean online,
    OffsetDateTime time
) {

    public static RobotConnectionResponse from(RobotConnectionEntity entity) {
        if (entity == null) {
            return null;
        }
        return from(entity, "online".equalsIgnoreCase(entity.getConnectionState()) || "connected".equalsIgnoreCase(entity.getConnectionState()));
    }

    public static RobotConnectionResponse from(RobotConnectionEntity entity, boolean online) {
        if (entity == null) {
            return null;
        }
        String state = entity.getConnectionState();
        return new RobotConnectionResponse(
            entity.getRobotId(),
            state,
            online,
            entity.getTime()
        );
    }
}
