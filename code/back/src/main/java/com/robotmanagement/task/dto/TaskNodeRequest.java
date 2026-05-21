package com.robotmanagement.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record TaskNodeRequest(
    @NotBlank(message = "nodeId 不能为空")
    String nodeId,

    @NotNull(message = "x 坐标不能为空")
    Double x,

    @NotNull(message = "y 坐标不能为空")
    Double y,

    Double theta,

    @NotBlank(message = "mapId 不能为空")
    String mapId,

    Map<String, Object> allowedDeviationXY,

    Double allowedDeviationTheta,

    List<Map<String, Object>> actions
) {
}
