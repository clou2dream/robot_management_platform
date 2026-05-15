package com.robotmanagement.operator.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record GrantRobotAccessRequest(
    @NotEmpty(message = "请选择机器人")
    List<UUID> robotIds
) {
}
