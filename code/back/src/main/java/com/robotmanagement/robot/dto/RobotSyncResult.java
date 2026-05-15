package com.robotmanagement.robot.dto;

public record RobotSyncResult(
    int created,
    int updated,
    int skipped
) {
}
