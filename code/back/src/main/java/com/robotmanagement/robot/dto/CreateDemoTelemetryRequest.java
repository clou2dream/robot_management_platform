package com.robotmanagement.robot.dto;

public record CreateDemoTelemetryRequest(
    String connectionState,
    Integer batterySoc,
    String operatingMode,
    String orderId,
    Double x,
    Double y,
    Double theta,
    String mapId
) {
}
