package com.robotmanagement.common.security;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefreshSession(
    UUID tokenId,
    UUID operatorId,
    UUID tenantId,
    OffsetDateTime issuedAt
) {
}
