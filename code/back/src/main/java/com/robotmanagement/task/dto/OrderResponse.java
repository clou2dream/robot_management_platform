package com.robotmanagement.task.dto;

import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.task.entity.OrderEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID robotId,
    String robotSn,
    String orderId,
    String status,
    Map<String, Object> payload,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static OrderResponse from(OrderEntity order, RobotEntity robot) {
        return new OrderResponse(
            order.getId(),
            order.getRobotId(),
            robot == null ? null : robot.getSerialNumber(),
            order.getOrderId(),
            order.getStatus(),
            order.getPayload(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
