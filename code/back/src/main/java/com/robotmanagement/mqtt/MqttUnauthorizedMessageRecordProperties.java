package com.robotmanagement.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.mqtt.unauthorized-message-record")
public record MqttUnauthorizedMessageRecordProperties(
    boolean enabled,
    int maxPayloadBytes,
    int maxPerMinutePerKey,
    Duration retention
) {
    public MqttUnauthorizedMessageRecordProperties {
        if (maxPayloadBytes <= 0) {
            maxPayloadBytes = 8192;
        }
        if (maxPerMinutePerKey <= 0) {
            maxPerMinutePerKey = 10;
        }
        if (retention == null) {
            retention = Duration.ofDays(7);
        }
    }
}
