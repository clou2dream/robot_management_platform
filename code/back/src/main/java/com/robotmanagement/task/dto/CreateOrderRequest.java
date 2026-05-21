package com.robotmanagement.task.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
    String orderId,

    Long orderUpdateId,

    String orderDescription,

    @DecimalMin(value = "0.1", message = "最大速度必须大于 0")
    Double maximumSpeed,

    @NotEmpty(message = "至少需要一个任务节点")
    @Valid
    List<TaskNodeRequest> nodes,

    @Valid
    List<TaskEdgeRequest> edges
) {
}
