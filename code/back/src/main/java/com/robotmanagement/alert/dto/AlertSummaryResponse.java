package com.robotmanagement.alert.dto;

public record AlertSummaryResponse(
    long openTotal,
    long resolvedTotal,
    long warning,
    long urgent,
    long critical,
    long fatal
) {
}
