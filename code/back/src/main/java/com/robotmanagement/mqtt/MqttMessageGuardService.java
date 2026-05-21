package com.robotmanagement.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class MqttMessageGuardService {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageGuardService.class);
    private static final Duration MESSAGE_DEDUP_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;

    public MqttMessageGuardService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Applies per-robot/channel ordering and headerId deduplication before telemetry is ingested.
     */
    public boolean accept(UUID robotId, String channel, JsonNode root) {
        Long headerId = headerId(root);
        if (headerId == null) {
            log.warn("Dropped MQTT {} message for robot {} because headerId is missing", channel, robotId);
            return false;
        }

        OffsetDateTime timestamp = timestamp(root);
        boolean timestampInvalid = timestamp == null;
        if (timestampInvalid) {
            timestamp = OffsetDateTime.now();
            log.warn("Accepted MQTT {} message for robot {} with invalid timestamp, using server time", channel, robotId);
        }

        if (isOlderThanLastTimestamp(robotId, channel, timestamp)) {
            log.warn("Dropped out-of-order MQTT {} message for robot {} timestamp={}", channel, robotId, timestamp);
            return false;
        }

        String headerKey = "robot:" + robotId + ":mqtt:" + channel + ":header:" + headerId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(headerKey))) {
            log.debug("Dropped duplicate MQTT {} message for robot {} headerId={}", channel, robotId, headerId);
            return false;
        }
        String timestampValue = timestamp.toInstant().toString();
        stringRedisTemplate.opsForValue().set(headerKey, timestampValue, MESSAGE_DEDUP_TTL);
        if (!timestampInvalid) {
            stringRedisTemplate.opsForValue().set(lastTimestampKey(robotId, channel), timestampValue, MESSAGE_DEDUP_TTL);
        }
        return true;
    }

    /**
     * Rejects messages whose timestamp is older than the latest accepted timestamp on the same channel.
     */
    private boolean isOlderThanLastTimestamp(UUID robotId, String channel, OffsetDateTime timestamp) {
        String last = stringRedisTemplate.opsForValue().get(lastTimestampKey(robotId, channel));
        if (last == null || last.isBlank()) {
            return false;
        }
        try {
            return timestamp.toInstant().isBefore(OffsetDateTime.parse(last).toInstant());
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Keeps last accepted timestamp separate per channel so state and visualization do not block each other.
     */
    private String lastTimestampKey(UUID robotId, String channel) {
        return "robot:" + robotId + ":mqtt:" + channel + ":lastTimestamp";
    }

    /**
     * Reads the protocol headerId as a long only when it is a valid integral number.
     */
    private Long headerId(JsonNode root) {
        JsonNode node = root.path("headerId");
        return node.isIntegralNumber() && node.canConvertToLong() ? node.asLong() : null;
    }

    /**
     * Parses the MQTT envelope timestamp and returns null when the robot sends an invalid value.
     */
    private OffsetDateTime timestamp(JsonNode root) {
        JsonNode node = root.path("timestamp");
        if (!node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.asText());
        } catch (Exception ignored) {
            return null;
        }
    }
}
