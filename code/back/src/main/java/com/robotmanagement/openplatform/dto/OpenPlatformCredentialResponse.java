package com.robotmanagement.openplatform.dto;

import com.robotmanagement.openplatform.entity.OpenPlatformCredentialEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OpenPlatformCredentialResponse(
    UUID id,
    String displayName,
    String appId,
    String apiKeyMasked,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static OpenPlatformCredentialResponse from(OpenPlatformCredentialEntity credential) {
        return new OpenPlatformCredentialResponse(
            credential.getId(),
            credential.getDisplayName(),
            credential.getAppId(),
            mask(credential.getApiKey()),
            credential.getCreatedAt(),
            credential.getUpdatedAt()
        );
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 6) {
            return "******";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 3);
    }
}
