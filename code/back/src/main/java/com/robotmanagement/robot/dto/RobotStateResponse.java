package com.robotmanagement.robot.dto;

import com.robotmanagement.robot.entity.RobotStateEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record RobotStateResponse(
    UUID robotId,
    Short batterySoc,
    String operatingMode,
    String orderId,
    RobotPositionResponse position,
    Map<String, Object> rawPayload,
    OffsetDateTime time
) {

    public static RobotStateResponse from(RobotStateEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RobotStateResponse(
            entity.getRobotId(),
            entity.getBatterySoc(),
            entity.getOperatingMode(),
            entity.getOrderId(),
            RobotPositionResponse.fromState(entity),
            entity.getRawPayload(),
            entity.getTime()
        );
    }
}
