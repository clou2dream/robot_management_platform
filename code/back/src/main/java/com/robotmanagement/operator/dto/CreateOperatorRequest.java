package com.robotmanagement.operator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOperatorRequest(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度应为 3-32 位")
    String username,

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度应为 6-64 位")
    String password,

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "admin|operator|viewer", message = "角色只能是 admin/operator/viewer")
    String role
) {
}
