package com.robotmanagement.mqtt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robotmanagement.mqtt.entity.MqttUnauthorizedMessageEntity;
import com.robotmanagement.mqtt.mapper.MqttUnauthorizedMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MqttUnauthorizedMessageService {

    private static final Logger log = LoggerFactory.getLogger(MqttUnauthorizedMessageService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final MqttUnauthorizedMessageMapper unauthorizedMessageMapper;
    private final ObjectMapper objectMapper;
    private final MqttUnauthorizedMessageRecordProperties properties;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public MqttUnauthorizedMessageService(
        MqttUnauthorizedMessageMapper unauthorizedMessageMapper,
        ObjectMapper objectMapper,
        MqttUnauthorizedMessageRecordProperties properties
    ) {
        this.unauthorizedMessageMapper = unauthorizedMessageMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Records a rejected inbound MQTT message for troubleshooting and security audit.
     * The method intentionally applies the feature switch, per-key rate limit, secret redaction,
     * and payload-size guard before touching PostgreSQL.
     */
    public void record(String topic, MqttTopicParts parts, JsonNode root, String reason) {
        if (!properties.enabled()) {
            return;
        }
        if (!acceptedByRateLimit(topic, parts, reason)) {
            return;
        }

        MqttUnauthorizedMessageEntity message = new MqttUnauthorizedMessageEntity();
        message.setId(UUID.randomUUID());
        message.setReceivedAt(OffsetDateTime.now());
        message.setTopic(topic);
        if (parts != null) {
            message.setManufacturer(parts.manufacturer());
            message.setSerialNumber(parts.serialNumber());
            message.setChannel(parts.channel());
        }
        JsonNode identity = root == null ? null : root.path("identity");
        if (identity != null && !identity.isMissingNode()) {
            message.setAppId(text(identity, "appid"));
            message.setRobotUniqueId(text(identity, "robotUniqueId"));
        }
        message.setReason(reason);
        message.setRawPayload(redactedPayload(root));
        unauthorizedMessageMapper.insert(message);
    }

    /**
     * Periodically removes stale audit rows so malicious or noisy clients cannot grow the table forever.
     */
    @Scheduled(
        fixedDelayString = "${app.mqtt.unauthorized-message-record.cleanup-delay-ms:3600000}",
        initialDelayString = "${app.mqtt.unauthorized-message-record.cleanup-initial-delay-ms:300000}"
    )
    public void cleanupExpiredRecords() {
        if (!properties.enabled()) {
            return;
        }
        Duration retention = properties.retention();
        if (retention == null || retention.isZero() || retention.isNegative()) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);
        int deleted = unauthorizedMessageMapper.delete(
            new LambdaQueryWrapper<MqttUnauthorizedMessageEntity>()
                .lt(MqttUnauthorizedMessageEntity::getReceivedAt, cutoff)
        );
        if (deleted > 0) {
            log.info("Cleaned {} expired MQTT unauthorized message records before {}", deleted, cutoff);
        }
    }

    /**
     * Converts the JSON payload to a map while masking credential secrets before persistence.
     */
    private Map<String, Object> redactedPayload(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        Map<String, Object> payload = objectMapper.convertValue(root, MAP_TYPE);
        Object identity = payload.get("identity");
        if (identity instanceof Map<?, ?> identityMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mutableIdentity = (Map<String, Object>) identityMap;
            if (mutableIdentity.containsKey("apisecret")) {
                mutableIdentity.put("apisecret", "***REDACTED***");
            }
            if (mutableIdentity.containsKey("apiSecret")) {
                mutableIdentity.put("apiSecret", "***REDACTED***");
            }
        }
        return limitPayloadSize(payload);
    }

    /**
     * Keeps only a bounded preview when an invalid client sends an oversized payload.
     */
    private Map<String, Object> limitPayloadSize(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            int bytes = json.getBytes(StandardCharsets.UTF_8).length;
            if (bytes <= properties.maxPayloadBytes()) {
                return payload;
            }

            int previewChars = Math.min(json.length(), properties.maxPayloadBytes());
            Map<String, Object> truncated = new LinkedHashMap<>();
            truncated.put("_truncated", true);
            truncated.put("_originalBytes", bytes);
            truncated.put("_payloadPreview", json.substring(0, previewChars));
            return truncated;
        } catch (Exception ignored) {
            return Map.of("_truncated", true, "_reason", "payload serialization failed");
        }
    }

    /**
     * Allows only a configured number of identical unauthorized records per minute.
     */
    private boolean acceptedByRateLimit(String topic, MqttTopicParts parts, String reason) {
        long windowStartedAt = System.currentTimeMillis() / 60_000L;
        String key = rateLimitKey(topic, parts, reason);
        RateWindow window = rateWindows.compute(key, (ignored, current) -> {
            if (current == null || current.windowStartedAt != windowStartedAt) {
                return new RateWindow(windowStartedAt);
            }
            return current;
        });
        int count = window.count.incrementAndGet();
        if (count <= properties.maxPerMinutePerKey()) {
            return true;
        }
        if (count == properties.maxPerMinutePerKey() + 1) {
            log.warn("Rate limited MQTT unauthorized message records for key {}", key);
        }
        return false;
    }

    /**
     * Groups repeated invalid messages by topic, robot identity, channel, and rejection reason.
     */
    private String rateLimitKey(String topic, MqttTopicParts parts, String reason) {
        String manufacturer = parts == null ? "" : parts.manufacturer();
        String serialNumber = parts == null ? "" : parts.serialNumber();
        String channel = parts == null ? "" : parts.channel();
        return String.join("|",
            Objects.toString(topic, ""),
            manufacturer,
            serialNumber,
            channel,
            Objects.toString(reason, "")
        );
    }

    /**
     * Reads a text field only when it is actually represented as JSON text.
     */
    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() ? node.asText() : null;
    }

    private static final class RateWindow {
        private final long windowStartedAt;
        private final AtomicInteger count = new AtomicInteger();

        private RateWindow(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
