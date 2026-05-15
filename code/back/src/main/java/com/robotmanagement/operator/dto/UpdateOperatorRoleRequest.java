package com.robotmanagement.operator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateOperatorRoleRequest(
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "admin|operator|viewer", message = "角色只能是 admin/operator/viewer")
    String role
) {
}
