package com.robotmanagement.openplatform.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveOpenPlatformCredentialRequest(
    String displayName,

    @NotBlank(message = "appid 不能为空")
    String appId,

    @NotBlank(message = "apikey 不能为空")
    String apiKey,

    @NotBlank(message = "apisecret 不能为空")
    String apiSecret
) {
}
