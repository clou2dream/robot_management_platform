package com.robotmanagement.robot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robotmanagement.alert.service.AlertService;
import com.robotmanagement.realtime.RealtimeMessagePublisher;
import com.robotmanagement.robot.dto.RobotConnectionResponse;
import com.robotmanagement.robot.dto.RobotPositionResponse;
import com.robotmanagement.robot.dto.RobotRealtimeResponse;
import com.robotmanagement.robot.dto.RobotResponse;
import com.robotmanagement.robot.dto.RobotStateResponse;
import com.robotmanagement.robot.entity.RobotConnectionEntity;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.entity.RobotFactsheetEntity;
import com.robotmanagement.robot.entity.RobotPositionEntity;
import com.robotmanagement.robot.entity.RobotStateEntity;
import com.robotmanagement.robot.mapper.RobotConnectionMapper;
import com.robotmanagement.robot.mapper.RobotFactsheetMapper;
import com.robotmanagement.robot.mapper.RobotPositionMapper;
import com.robotmanagement.robot.mapper.RobotStateMapper;
import com.robotmanagement.task.dto.OrderResponse;
import com.robotmanagement.task.entity.OrderEntity;
import com.robotmanagement.task.mapper.OrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Service
public class RobotTelemetryIngestionService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RobotConnectionMapper connectionMapper;
    private final RobotStateMapper stateMapper;
    private final RobotPositionMapper positionMapper;
    private final RobotFactsheetMapper factsheetMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeMessagePublisher realtimeMessagePublisher;
    private final AlertService alertService;
    private final OrderMapper orderMapper;
    private final Duration onlineTtl;

    public RobotTelemetryIngestionService(
        RobotConnectionMapper connectionMapper,
        RobotStateMapper stateMapper,
        RobotPositionMapper positionMapper,
        RobotFactsheetMapper factsheetMapper,
        StringRedisTemplate stringRedisTemplate,
        ObjectMapper objectMapper,
        RealtimeMessagePublisher realtimeMessagePublisher,
        AlertService alertService,
        OrderMapper orderMapper,
        @Value("${app.robot.online-ttl:PT10S}") Duration onlineTtl
    ) {
        this.connectionMapper = connectionMapper;
        this.stateMapper = stateMapper;
        this.positionMapper = positionMapper;
        this.factsheetMapper = factsheetMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.realtimeMessagePublisher = realtimeMessagePublisher;
        this.alertService = alertService;
        this.orderMapper = orderMapper;
        this.onlineTtl = sanitizeOnlineTtl(onlineTtl);
    }

    /**
     * Persists a connection event and refreshes the Redis online marker when the robot reports ONLINE.
     */
    @Transactional
    public void ingestConnection(RobotEntity robot, JsonNode root) {
        OffsetDateTime time = timestamp(root);
        String connectionState = defaultString(text(root, "connectionState"), "ONLINE").toUpperCase();

        RobotConnectionEntity connection = new RobotConnectionEntity();
        connection.setTime(time);
        connection.setTenantId(robot.getTenantId());
        connection.setRobotId(robot.getId());
        connection.setConnectionState(connectionState);
        connectionMapper.insert(connection);

        if (isOnline(connectionState)) {
            markOnline(robot);
            alertService.resolveSystemAlert(robot.getTenantId(), robot.getId(), "CONNECTION_BROKEN");
        } else {
            markNotOnline(robot.getId(), connectionState);
            if (connectionState.contains("BROKEN")) {
                alertService.raiseSystemAlert(
                    robot.getTenantId(),
                    robot.getId(),
                    "CRITICAL",
                    "CONNECTION_BROKEN",
                    "机器人连接异常中断。",
                    "检查机器人网络、EMQX 连接状态和现场供电。",
                    raw(root)
                );
            }
        }

        RobotConnectionResponse response = RobotConnectionResponse.from(connection);
        realtimeMessagePublisher.robotConnection(robot.getId(), response);
        realtimeMessagePublisher.robotRealtime(robot.getId(), realtime(robot, response, null, null));
    }

    /**
     * Creates a synthetic CONNECTION_BROKEN event when no valid telemetry refreshes the online TTL in time.
     */
    @Transactional
    public void ingestConnectionBrokenByTimeout(RobotEntity robot, OffsetDateTime detectedAt, OffsetDateTime lastSeenAt) {
        RobotConnectionEntity connection = new RobotConnectionEntity();
        connection.setTime(detectedAt);
        connection.setTenantId(robot.getTenantId());
        connection.setRobotId(robot.getId());
        connection.setConnectionState("CONNECTION_BROKEN");
        connectionMapper.insert(connection);

        markNotOnline(robot.getId(), "CONNECTION_BROKEN");
        alertService.raiseSystemAlert(
            robot.getTenantId(),
            robot.getId(),
            "CRITICAL",
            "CONNECTION_BROKEN",
            "机器人在线心跳超时，判定为连接异常中断。",
            "检查机器人 state/visualization 上报、EMQX 连接状态、现场网络和供电。",
            Map.of(
                "source", "online-ttl",
                "lastSeenAt", lastSeenAt == null ? "" : lastSeenAt.toString(),
                "detectedAt", detectedAt.toString()
            )
        );

        RobotConnectionResponse response = RobotConnectionResponse.from(connection);
        realtimeMessagePublisher.robotConnection(robot.getId(), response);
        realtimeMessagePublisher.robotRealtime(robot.getId(), realtime(robot, response, null, null));
    }

    /**
     * Persists a full state frame, refreshes online TTL, evaluates alerts, and updates order progress.
     */
    @Transactional
    public void ingestState(RobotEntity robot, JsonNode root) {
        OffsetDateTime time = timestamp(root);
        JsonNode positionNode = root.path("agvPosition");
        Short batterySoc = batterySoc(root);
        String operatingMode = text(root, "operatingMode");
        String orderId = text(root, "orderId");

        RobotStateEntity state = new RobotStateEntity();
        state.setTime(time);
        state.setTenantId(robot.getTenantId());
        state.setRobotId(robot.getId());
        state.setPosX(number(positionNode, "x"));
        state.setPosY(number(positionNode, "y"));
        state.setPosTheta(number(positionNode, "theta"));
        state.setMapId(text(positionNode, "mapId"));
        state.setBatterySoc(batterySoc);
        state.setOperatingMode(operatingMode);
        state.setOrderId(orderId);
        state.setRawPayload(raw(root));
        stateMapper.insert(state);

        markOnline(robot);
        writeSnapshot("state", robot.getId(), RobotStateResponse.from(state, false));
        evaluateStateAlerts(robot, root, batterySoc);
        updateOrderStatus(robot, orderId, root);

        RobotStateResponse response = RobotStateResponse.from(state, false);
        realtimeMessagePublisher.robotState(robot.getId(), response);
        realtimeMessagePublisher.robotRealtime(robot.getId(), realtime(robot, null, response, null));
    }

    /**
     * Persists a visualization position frame and refreshes online TTL.
     */
    @Transactional
    public void ingestPosition(RobotEntity robot, JsonNode root) {
        OffsetDateTime time = timestamp(root);
        JsonNode positionNode = root.path("agvPosition");

        RobotPositionEntity position = new RobotPositionEntity();
        position.setTime(time);
        position.setTenantId(robot.getTenantId());
        position.setRobotId(robot.getId());
        position.setPosX(number(positionNode, "x"));
        position.setPosY(number(positionNode, "y"));
        position.setPosTheta(number(positionNode, "theta"));
        position.setMapId(text(positionNode, "mapId"));
        positionMapper.insert(position);

        markOnline(robot);
        RobotPositionResponse response = RobotPositionResponse.from(position);
        writeSnapshot("position", robot.getId(), response);
        realtimeMessagePublisher.robotPosition(robot.getId(), response);
        realtimeMessagePublisher.robotRealtime(robot.getId(), realtime(robot, null, null, response));
    }

    /**
     * Stores the latest factsheet snapshot for task and instantAction capability validation.
     */
    @Transactional
    public void ingestFactsheet(RobotEntity robot, JsonNode root) {
        OffsetDateTime time = timestamp(root);
        RobotFactsheetEntity factsheet = factsheetMapper.selectById(robot.getId());
        if (factsheet == null) {
            factsheet = new RobotFactsheetEntity();
            factsheet.setRobotId(robot.getId());
            factsheet.setTenantId(robot.getTenantId());
            factsheet.setReceivedAt(time);
            factsheet.setRawPayload(raw(root));
            factsheetMapper.insert(factsheet);
        } else {
            factsheet.setTenantId(robot.getTenantId());
            factsheet.setReceivedAt(time);
            factsheet.setRawPayload(raw(root));
            factsheetMapper.updateById(factsheet);
        }
        writeSnapshot("factsheet", robot.getId(), raw(root));
    }

    /**
     * Refreshes the Redis keys that the robot list and offline monitor use as the online source of truth.
     */
    public void markOnline(UUID robotId) {
        stringRedisTemplate.opsForValue().set(onlineKey(robotId), "1", onlineTtl);
        stringRedisTemplate.opsForValue().set(lastSeenKey(robotId), OffsetDateTime.now().toString());
        stringRedisTemplate.opsForValue().set(connectionStateKey(robotId), "ONLINE");
    }

    /**
     * Reads the last server-side time at which valid telemetry refreshed the online TTL.
     */
    public OffsetDateTime lastSeenAt(UUID robotId) {
        String value = stringRedisTemplate.opsForValue().get(lastSeenKey(robotId));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Reads the latest connection state cached in Redis for offline timeout decisions.
     */
    public String lastConnectionState(UUID robotId) {
        return stringRedisTemplate.opsForValue().get(connectionStateKey(robotId));
    }

    /**
     * Checks whether the short-lived online marker is still present in Redis.
     */
    public boolean isMarkedOnline(UUID robotId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(onlineKey(robotId)));
    }

    /**
     * Refreshes online state and resolves connection-broken alerts after a robot recovers.
     */
    private void markOnline(RobotEntity robot) {
        String previousState = lastConnectionState(robot.getId());
        markOnline(robot.getId());
        if (StringUtils.hasText(previousState) && !"ONLINE".equalsIgnoreCase(previousState)) {
            alertService.resolveSystemAlert(robot.getTenantId(), robot.getId(), "CONNECTION_BROKEN");
        }
    }

    /**
     * Removes the online marker while retaining the last non-online connection state for diagnostics.
     */
    private void markNotOnline(UUID robotId, String connectionState) {
        stringRedisTemplate.delete(onlineKey(robotId));
        stringRedisTemplate.opsForValue().set(connectionStateKey(robotId), connectionState);
    }

    /**
     * Builds a partial realtime patch for WebSocket/STOMP subscribers.
     */
    private RobotRealtimeResponse realtime(
        RobotEntity robot,
        RobotConnectionResponse connection,
        RobotStateResponse state,
        RobotPositionResponse position
    ) {
        return new RobotRealtimeResponse(
            RobotResponse.from(robot, true),
            connection,
            state,
            position
        );
    }

    /**
     * Converts state fields into system alerts for low battery, emergency stop, and fatal errors.
     */
    private void evaluateStateAlerts(RobotEntity robot, JsonNode root, Short batterySoc) {
        if (batterySoc != null && batterySoc < 10) {
            alertService.raiseSystemAlert(
                robot.getTenantId(),
                robot.getId(),
                "URGENT",
                "LOW_BATTERY",
                "机器人电量低于 10%。",
                "安排机器人返回充电点或暂停高风险任务。",
                raw(root)
            );
        } else {
            alertService.resolveSystemAlert(robot.getTenantId(), robot.getId(), "LOW_BATTERY");
        }

        String eStop = text(root.path("safetyState"), "eStop");
        if (StringUtils.hasText(eStop) && !"NONE".equalsIgnoreCase(eStop)) {
            alertService.raiseSystemAlert(
                robot.getTenantId(),
                robot.getId(),
                "CRITICAL",
                "E_STOP",
                "机器人安全急停处于触发状态。",
                "现场确认安全链路，排除急停原因后复位。",
                raw(root.path("safetyState"))
            );
        } else {
            alertService.resolveSystemAlert(robot.getTenantId(), robot.getId(), "E_STOP");
        }

        JsonNode errors = root.path("errors");
        if (errors.isArray()) {
            Iterator<JsonNode> iterator = errors.elements();
            while (iterator.hasNext()) {
                JsonNode error = iterator.next();
                String level = text(error, "errorLevel");
                if ("FATAL".equalsIgnoreCase(level)) {
                    String errorType = defaultString(text(error, "errorType"), "FATAL_ERROR");
                    alertService.raiseSystemAlert(
                        robot.getTenantId(),
                        robot.getId(),
                        "FATAL",
                        errorType,
                        defaultString(text(error, "errorDescription"), "机器人上报 FATAL 错误。"),
                        "查看机器人原始错误并联系现场人员处理。",
                        raw(error)
                    );
                }
            }
        }
    }

    /**
     * Advances local order status from robot-reported state frames.
     */
    private void updateOrderStatus(RobotEntity robot, String orderId, JsonNode root) {
        if (!StringUtils.hasText(orderId)) {
            return;
        }

        OrderEntity order = orderMapper.selectOne(
            new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getTenantId, robot.getTenantId())
                .eq(OrderEntity::getRobotId, robot.getId())
                .eq(OrderEntity::getOrderId, orderId)
                .last("limit 1")
        );
        if (order == null
            || "cancelled".equals(order.getStatus())
            || "completed".equals(order.getStatus())
            || "failed".equals(order.getStatus())) {
            return;
        }

        String nextStatus = reportedOrderStatus(root);
        if (nextStatus == null) {
            nextStatus = containsFatalError(root) ? "failed" : "active";
        }
        if (!nextStatus.equals(order.getStatus())) {
            order.setStatus(nextStatus);
            order.setUpdatedAt(OffsetDateTime.now());
            orderMapper.updateById(order);
            realtimeMessagePublisher.orderStatus(robot.getId(), OrderResponse.from(order, robot));
        }
    }

    /**
     * Normalizes vendor-reported order states into the platform's internal order statuses.
     */
    private String reportedOrderStatus(JsonNode root) {
        String value = defaultString(text(root, "orderStatus"), text(root, "orderState"));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "completed", "complete", "finished", "done" -> "completed";
            case "failed", "failure", "error" -> "failed";
            case "cancelled", "canceled" -> "cancelled";
            case "active", "running", "executing" -> "active";
            default -> null;
        };
    }

    /**
     * Treats any FATAL error in a state frame as a failed order unless an explicit order status exists.
     */
    private boolean containsFatalError(JsonNode root) {
        JsonNode errors = root.path("errors");
        if (!errors.isArray()) {
            return false;
        }
        Iterator<JsonNode> iterator = errors.elements();
        while (iterator.hasNext()) {
            if ("FATAL".equalsIgnoreCase(text(iterator.next(), "errorLevel"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes a best-effort Redis snapshot for fast UI reads; database rows remain authoritative.
     */
    private void writeSnapshot(String kind, UUID robotId, Object payload) {
        try {
            stringRedisTemplate.opsForValue().set("robot:" + robotId + ":" + kind, objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            // Redis snapshots are an acceleration path; persisted rows remain the source of truth.
        }
    }

    private String onlineKey(UUID robotId) {
        return "robot:" + robotId + ":online";
    }

    private String lastSeenKey(UUID robotId) {
        return "robot:" + robotId + ":last-seen";
    }

    private String connectionStateKey(UUID robotId) {
        return "robot:" + robotId + ":connection-state";
    }

    /**
     * Parses MQTT timestamps and falls back to server time when the robot omits or malforms them.
     */
    private OffsetDateTime timestamp(JsonNode root) {
        String value = text(root, "timestamp");
        if (!StringUtils.hasText(value)) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            return OffsetDateTime.now();
        }
    }

    /**
     * Extracts battery charge from the protocol-preferred batteryState object, with a legacy fallback.
     */
    private Short batterySoc(JsonNode root) {
        JsonNode battery = root.path("batteryState");
        Double value = number(battery, "batteryCharge");
        if (value == null) {
            value = number(root, "batterySoc");
        }
        if (value == null) {
            return null;
        }
        return (short) Math.max(0, Math.min(100, Math.round(value)));
    }

    /**
     * Reads a numeric field whether the robot encoded it as a JSON number or a numeric string.
     */
    private Double number(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
     * Keeps ONLINE as the only state that refreshes the online marker from connection messages.
     */
    private boolean isOnline(String connectionState) {
        return "ONLINE".equalsIgnoreCase(connectionState);
    }

    /**
     * Converts a Jackson tree into a JSONB-friendly map for persistence.
     */
    private Map<String, Object> raw(JsonNode root) {
        return objectMapper.convertValue(root, MAP_TYPE);
    }

    /**
     * Prevents invalid configuration from disabling Redis TTL expiration.
     */
    private Duration sanitizeOnlineTtl(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return Duration.ofSeconds(10);
        }
        return value;
    }
}
