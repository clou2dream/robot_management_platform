package com.robotmanagement.robot.dto;

public record RobotRealtimeResponse(
    RobotResponse robot,
    RobotConnectionResponse connection,
    RobotStateResponse state,
    RobotPositionResponse position
) {
}
