package com.robotmanagement.alert.dto;

import java.util.UUID;

public record CreateDemoAlertRequest(
    UUID robotId,
    String level,
    String errorType,
    String description,
    String hint
) {
}
