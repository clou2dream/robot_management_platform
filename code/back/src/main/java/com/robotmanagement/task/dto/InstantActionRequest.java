package com.robotmanagement.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InstantActionRequest(
    @NotBlank(message = "actionType 不能为空")
    @Pattern(regexp = "stopPause|startPause|cancelOrder|factsheetRequest", message = "暂不支持该即时指令")
    String actionType,

    String orderId
) {
}
