package com.robotmanagement.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TaskNodeRequest(
    @NotBlank(message = "nodeId 不能为空")
    String nodeId,

    @NotNull(message = "x 坐标不能为空")
    Double x,

    @NotNull(message = "y 坐标不能为空")
    Double y,

    Double theta
) {
}
