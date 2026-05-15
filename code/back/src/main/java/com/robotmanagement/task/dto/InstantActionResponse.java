package com.robotmanagement.task.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record InstantActionResponse(
    UUID robotId,
    String actionType,
    Map<String, Object> payload,
    OffsetDateTime createdAt
) {
}
