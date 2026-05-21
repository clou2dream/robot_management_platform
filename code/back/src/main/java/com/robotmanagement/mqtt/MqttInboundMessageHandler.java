package com.robotmanagement.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.service.RobotTelemetryIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MqttInboundMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttInboundMessageHandler.class);

    private final ObjectMapper objectMapper;
    private final RobotTelemetryIngestionService telemetryIngestionService;
    private final MqttRobotAuthenticationService authenticationService;
    private final MqttMessageGuardService messageGuardService;
    private final MqttUnauthorizedMessageService unauthorizedMessageService;

    public MqttInboundMessageHandler(
        ObjectMapper objectMapper,
        RobotTelemetryIngestionService telemetryIngestionService,
        MqttRobotAuthenticationService authenticationService,
        MqttMessageGuardService messageGuardService,
        MqttUnauthorizedMessageService unauthorizedMessageService
    ) {
        this.objectMapper = objectMapper;
        this.telemetryIngestionService = telemetryIngestionService;
        this.authenticationService = authenticationService;
        this.messageGuardService = messageGuardService;
        this.unauthorizedMessageService = unauthorizedMessageService;
    }

    /**
     * Entry point for every subscribed MQTT message; parses, validates, and routes by protocol channel.
     */
    public void handle(String topic, String payload) {
        MqttTopicParts parts = parseTopic(topic);
        if (parts == null) {
            log.debug("Ignored unsupported MQTT topic {}", topic);
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!validEnvelope(topic, parts, root)) {
                return;
            }

            switch (parts.channel()) {
                case "connection" -> handleConnection(topic, parts, root);
                case "state" -> handleAuthenticated(topic, parts, root, robot -> telemetryIngestionService.ingestState(robot, root));
                case "visualization" -> handleAuthenticated(topic, parts, root, robot -> telemetryIngestionService.ingestPosition(robot, root));
                case "factsheet" -> handleAuthenticated(topic, parts, root, robot -> telemetryIngestionService.ingestFactsheet(robot, root));
                default -> log.debug("Ignored MQTT channel {}", parts.channel());
            }
        } catch (Exception ex) {
            log.warn("Failed to handle MQTT topic {}: {}", topic, ex.getMessage());
        }
    }

    /**
     * Handles connection lifecycle messages, requiring full identity credentials only for ONLINE.
     */
    private void handleConnection(String topic, MqttTopicParts parts, JsonNode root) {
        String connectionState = defaultString(text(root, "connectionState"), "ONLINE").toUpperCase();
        if (!isSupportedConnectionState(connectionState)) {
            unauthorizedMessageService.record(topic, parts, root, "不支持的 connectionState: " + connectionState);
            return;
        }

        RobotEntity robot = "ONLINE".equals(connectionState)
            ? authenticationService.authenticateOnline(topic, parts, root)
            : authenticationService.authenticatedRobot(topic, parts, root);
        if (robot == null || !messageGuardService.accept(robot.getId(), parts.channel(), root)) {
            return;
        }

        telemetryIngestionService.ingestConnection(robot, root);
        if (!"ONLINE".equals(connectionState)) {
            authenticationService.clearSession(parts);
        }
    }

    /**
     * Handles telemetry channels that must reuse a previously established ONLINE auth session.
     */
    private void handleAuthenticated(
        String topic,
        MqttTopicParts parts,
        JsonNode root,
        RobotTelemetryConsumer consumer
    ) {
        RobotEntity robot = authenticationService.authenticatedRobot(topic, parts, root);
        if (robot == null || !messageGuardService.accept(robot.getId(), parts.channel(), root)) {
            return;
        }
        consumer.accept(robot);
        authenticationService.touchSession(parts);
    }

    /**
     * Accepts only the current five-segment UAGV topic shape.
     */
    private MqttTopicParts parseTopic(String topic) {
        String[] parts = topic.split("/");
        if (parts.length != 5 || !"uagv".equals(parts[0]) || !"v3".equals(parts[1])) {
            return null;
        }
        String channel = parts[4];
        if (!channel.equals("state")
            && !channel.equals("visualization")
            && !channel.equals("connection")
            && !channel.equals("factsheet")) {
            return null;
        }
        return new MqttTopicParts(parts[2], parts[3], channel);
    }

    /**
     * Validates the common MQTT envelope before channel-specific logic runs.
     */
    private boolean validEnvelope(String topic, MqttTopicParts parts, JsonNode root) {
        if (!"3.0.0".equals(text(root, "version"))) {
            unauthorizedMessageService.record(topic, parts, root, "version 必须为 3.0.0");
            return false;
        }
        JsonNode headerId = root.path("headerId");
        if (!headerId.isIntegralNumber() || !headerId.canConvertToLong()) {
            unauthorizedMessageService.record(topic, parts, root, "headerId 缺失或不是数字");
            return false;
        }
        if (!StringUtils.hasText(text(root, "timestamp"))) {
            unauthorizedMessageService.record(topic, parts, root, "timestamp 缺失");
            return false;
        }
        String manufacturer = text(root, "manufacturer");
        String serialNumber = text(root, "serialNumber");
        if (!parts.manufacturer().equals(manufacturer) || !parts.serialNumber().equals(serialNumber)) {
            unauthorizedMessageService.record(topic, parts, root, "payload 与 Topic 中的 manufacturer/serialNumber 不一致");
            return false;
        }
        return true;
    }

    /**
     * Reads a text field only when it is actually represented as JSON text.
     */
    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() ? node.asText() : null;
    }

    /**
     * Chooses a fallback only when the primary string is blank.
     */
    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * Keeps accepted connection states explicit so unknown values fail closed.
     */
    private boolean isSupportedConnectionState(String connectionState) {
        return "ONLINE".equals(connectionState)
            || "OFFLINE".equals(connectionState)
            || "HIBERNATING".equals(connectionState)
            || "CONNECTION_BROKEN".equals(connectionState);
    }

    @FunctionalInterface
    private interface RobotTelemetryConsumer {
        void accept(RobotEntity robot);
    }
}
