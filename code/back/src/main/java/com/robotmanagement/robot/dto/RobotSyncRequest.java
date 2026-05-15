package com.robotmanagement.robot.dto;

import jakarta.validation.Valid;

import java.util.List;

public record RobotSyncRequest(
    @Valid
    List<AuthorizedRobotSyncItem> robots
) {
}
