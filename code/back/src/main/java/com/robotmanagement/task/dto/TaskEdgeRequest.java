package com.robotmanagement.task.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record TaskEdgeRequest(
    String edgeId,

    @NotBlank(message = "startNodeId 不能为空")
    String startNodeId,

    @NotBlank(message = "endNodeId 不能为空")
    String endNodeId,

    @DecimalMin(value = "0.1", message = "边最大速度必须大于 0")
    Double maximumSpeed,

    Double orientation,

    String orientationType,

    Boolean reachOrientationBeforeEntering,

    List<Map<String, Object>> actions
) {
}
