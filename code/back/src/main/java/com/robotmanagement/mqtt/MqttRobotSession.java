package com.robotmanagement.mqtt;

import java.util.UUID;

public record MqttRobotSession(
    UUID robotId,
    UUID tenantId,
    UUID operatorId,
    String appId,
    String robotUniqueId,
    String manufacturer,
    String serialNumber,
    String authenticatedAt
) {
}
