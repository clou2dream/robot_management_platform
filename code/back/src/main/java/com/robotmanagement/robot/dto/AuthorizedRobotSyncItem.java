package com.robotmanagement.robot.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthorizedRobotSyncItem(
    @NotBlank(message = "externalRobotId 不能为空")
    String externalRobotId,

    @NotBlank(message = "serialNumber 不能为空")
    String serialNumber,

    String manufacturer,
    String sourceAppId,
    String status
) {
}
