package com.robotmanagement.robot.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RobotSyncStatusResponse(
    UUID robotId,
    String externalRobotId,
    String serialNumber,
    String sourceStatus,
    OffsetDateTime syncedAt
) {
}
