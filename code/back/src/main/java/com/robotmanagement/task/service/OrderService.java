package com.robotmanagement.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.mqtt.MqttClientService;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.entity.RobotFactsheetEntity;
import com.robotmanagement.robot.mapper.RobotFactsheetMapper;
import com.robotmanagement.robot.mapper.RobotMapper;
import com.robotmanagement.task.dto.CreateOrderRequest;
import com.robotmanagement.task.dto.InstantActionRequest;
import com.robotmanagement.task.dto.InstantActionResponse;
import com.robotmanagement.task.dto.OrderResponse;
import com.robotmanagement.task.dto.TaskEdgeRequest;
import com.robotmanagement.task.dto.TaskNodeRequest;
import com.robotmanagement.task.entity.OrderEntity;
import com.robotmanagement.task.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final RobotMapper robotMapper;
    private final OperatorRobotAccessMapper accessMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MqttClientService mqttClientService;
    private final RobotFactsheetMapper factsheetMapper;
    private final boolean requireFactsheetCapabilities;

    public OrderService(
        OrderMapper orderMapper,
        RobotMapper robotMapper,
        OperatorRobotAccessMapper accessMapper,
        StringRedisTemplate stringRedisTemplate,
        MqttClientService mqttClientService,
        RobotFactsheetMapper factsheetMapper,
        @Value("${app.task.require-factsheet-capabilities:false}") boolean requireFactsheetCapabilities
    ) {
        this.orderMapper = orderMapper;
        this.robotMapper = robotMapper;
        this.accessMapper = accessMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mqttClientService = mqttClientService;
        this.factsheetMapper = factsheetMapper;
        this.requireFactsheetCapabilities = requireFactsheetCapabilities;
    }

    /**
     * Builds, validates, publishes, and persists a new order for the selected robot.
     */
    @Transactional
    public OrderResponse createOrder(UUID robotId, CreateOrderRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser.isViewer()) {
            throw BusinessException.forbidden("viewer 不能下发任务");
        }

        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        assertRobotOnline(robotId);
        assertRobotAuthenticated(robot);
        assertOrderSupported(robotId, request);
        String orderId = StringUtils.hasText(request.orderId())
            ? request.orderId().trim()
            : "order-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        long headerId = nextHeaderId(robotId);
        Map<String, Object> payload = buildOrderPayload(robot, orderId, request, now, headerId);

        publish(robot, "order", payload);

        OrderEntity order = new OrderEntity();
        order.setId(UUID.randomUUID());
        order.setTenantId(currentUser.tenantId());
        order.setRobotId(robotId);
        order.setOrderId(orderId);
        order.setStatus("pending");
        order.setPayload(payload);
        order.setCreatedBy(currentUser.operatorId());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        return OrderResponse.from(order, robot);
    }

    /**
     * Returns the robot's task history with optional status filtering.
     */
    public PageResult<OrderResponse> listOrders(UUID robotId, long page, long size, String status) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getTenantId, currentUser.tenantId())
            .eq(OrderEntity::getRobotId, robotId)
            .orderByDesc(OrderEntity::getCreatedAt);

        if (StringUtils.hasText(status)) {
            wrapper.eq(OrderEntity::getStatus, status.trim());
        }

        Page<OrderEntity> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        List<OrderResponse> records = result.getRecords()
            .stream()
            .map(order -> OrderResponse.from(order, robot))
            .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * Loads a single order record and re-checks tenant and robot ownership before returning it.
     */
    public OrderResponse getOrder(UUID robotId, UUID orderRecordId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        OrderEntity order = orderMapper.selectById(orderRecordId);
        if (order == null || !robotId.equals(order.getRobotId()) || !currentUser.tenantId().equals(order.getTenantId())) {
            throw BusinessException.notFound("任务不存在");
        }
        return OrderResponse.from(order, robot);
    }

    /**
     * Publishes a cancelOrder instant action and marks the order cancelled locally.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID robotId, UUID orderRecordId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser.isViewer()) {
            throw BusinessException.forbidden("viewer 不能取消任务");
        }

        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        OrderEntity order = orderMapper.selectById(orderRecordId);
        if (order == null || !robotId.equals(order.getRobotId()) || !currentUser.tenantId().equals(order.getTenantId())) {
            throw BusinessException.notFound("任务不存在");
        }
        if (!"pending".equals(order.getStatus()) && !"active".equals(order.getStatus())) {
            throw BusinessException.conflict("只有 pending/active 任务可以取消");
        }

        assertRobotOnline(robotId);
        assertRobotAuthenticated(robot);
        assertInstantActionSupported(robotId, "cancelOrder");
        publish(
            robot,
            "instantActions",
            buildInstantActionPayload(robot, new InstantActionRequest("cancelOrder", order.getOrderId()), OffsetDateTime.now(), nextHeaderId(robotId))
        );
        order.setStatus("cancelled");
        order.setUpdatedAt(OffsetDateTime.now());
        orderMapper.updateById(order);
        return OrderResponse.from(order, robot);
    }

    /**
     * Publishes a standalone instant action after online/authenticated/capability checks succeed.
     */
    public InstantActionResponse createInstantAction(UUID robotId, InstantActionRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser.isViewer()) {
            throw BusinessException.forbidden("viewer 不能下发即时指令");
        }

        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        OffsetDateTime now = OffsetDateTime.now();
        assertRobotOnline(robotId);
        assertRobotAuthenticated(robot);
        assertInstantActionSupported(robotId, request.actionType());
        Map<String, Object> payload = buildInstantActionPayload(robot, request, now, nextHeaderId(robotId));
        publish(robot, "instantActions", payload);
        return new InstantActionResponse(robotId, request.actionType(), payload, now);
    }

    /**
     * Ensures the current user can see the robot and that the robot still belongs to the same tenant.
     */
    private RobotEntity loadAccessibleRobot(UUID robotId, CurrentUser currentUser) {
        RobotEntity robot = robotMapper.selectById(robotId);
        if (robot == null || !currentUser.tenantId().equals(robot.getTenantId())) {
            throw BusinessException.notFound("机器人不存在");
        }
        if (!"active".equals(robot.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "机器人来源状态不是 active");
        }
        if (currentUser.isAdmin()) {
            return robot;
        }

        Long count = accessMapper.selectCount(
            new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                .eq(OperatorRobotAccessEntity::getOperatorId, currentUser.operatorId())
                .eq(OperatorRobotAccessEntity::getRobotId, robotId)
        );
        if (count == null || count == 0) {
            throw BusinessException.forbidden("无权操作该机器人");
        }
        return robot;
    }

    /**
     * Builds the VDA5050-style order payload from the submitted nodes and edges.
     */
    private Map<String, Object> buildOrderPayload(
        RobotEntity robot,
        String orderId,
        CreateOrderRequest request,
        OffsetDateTime now,
        long headerId
    ) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        List<TaskNodeRequest> sourceNodes = request.nodes();
        List<TaskEdgeRequest> sourceEdges = request.edges();

        for (int i = 0; i < sourceNodes.size(); i++) {
            TaskNodeRequest source = sourceNodes.get(i);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("nodeId", source.nodeId());
            node.put("sequenceId", i * 2L);
            node.put("released", true);
            Map<String, Object> nodePosition = new LinkedHashMap<>();
            nodePosition.put("x", source.x());
            nodePosition.put("y", source.y());
            putIfNotNull(nodePosition, "theta", source.theta());
            nodePosition.put("mapId", source.mapId());
            if (source.allowedDeviationXY() != null && !source.allowedDeviationXY().isEmpty()) {
                nodePosition.put("allowedDeviationXY", source.allowedDeviationXY());
            }
            if (source.allowedDeviationTheta() != null) {
                nodePosition.put("allowedDeviationTheta", source.allowedDeviationTheta());
            }
            node.put("nodePosition", nodePosition);
            node.put("actions", defaultList(source.actions()));
            nodes.add(node);

            if (i > 0) {
                TaskNodeRequest previous = sourceNodes.get(i - 1);
                TaskEdgeRequest requestedEdge = edgeAt(sourceEdges, i - 1);
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("edgeId", StringUtils.hasText(requestedEdge == null ? null : requestedEdge.edgeId())
                    ? requestedEdge.edgeId().trim()
                    : previous.nodeId() + "-" + source.nodeId());
                edge.put("sequenceId", i * 2L - 1L);
                edge.put("released", true);
                edge.put("startNodeId", StringUtils.hasText(requestedEdge == null ? null : requestedEdge.startNodeId())
                    ? requestedEdge.startNodeId().trim()
                    : previous.nodeId());
                edge.put("endNodeId", StringUtils.hasText(requestedEdge == null ? null : requestedEdge.endNodeId())
                    ? requestedEdge.endNodeId().trim()
                    : source.nodeId());
                edge.put("maximumSpeed", edgeMaximumSpeed(request, requestedEdge));
                putIfNotNull(edge, "orientation", requestedEdge == null ? null : requestedEdge.orientation());
                edge.put("orientationType", defaultString(requestedEdge == null ? null : requestedEdge.orientationType(), "TANGENTIAL"));
                putIfNotNull(edge, "reachOrientationBeforeEntering", requestedEdge == null ? null : requestedEdge.reachOrientationBeforeEntering());
                edge.put("actions", requestedEdge == null ? List.of() : defaultList(requestedEdge.actions()));
                edges.add(edge);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headerId", headerId);
        payload.put("timestamp", now.toString());
        payload.put("version", "3.0.0");
        payload.put("manufacturer", robot.getManufacturer());
        payload.put("serialNumber", robot.getSerialNumber());
        payload.put("orderId", orderId);
        payload.put("orderUpdateId", request.orderUpdateId() == null ? 0 : request.orderUpdateId());
        if (StringUtils.hasText(request.orderDescription())) {
            payload.put("orderDescription", request.orderDescription().trim());
        }
        payload.put("nodes", nodes);
        payload.put("edges", edges);
        return payload;
    }

    /**
     * Builds the VDA5050-style instantAction payload with a fresh headerId.
     */
    private Map<String, Object> buildInstantActionPayload(
        RobotEntity robot,
        InstantActionRequest request,
        OffsetDateTime now,
        long headerId
    ) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("actionType", request.actionType());
        action.put("actionId", "action-" + UUID.randomUUID());
        action.put("blockingType", "HARD");
        action.put("actionParameters", StringUtils.hasText(request.orderId())
            ? List.of(Map.of("key", "orderId", "value", request.orderId()))
            : List.of()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headerId", headerId);
        payload.put("timestamp", now.toString());
        payload.put("version", "3.0.0");
        payload.put("manufacturer", robot.getManufacturer());
        payload.put("serialNumber", robot.getSerialNumber());
        payload.put("actions", List.of(action));
        return payload;
    }

    /**
     * Verifies that the robot is currently online in Redis before publishing anything to MQTT.
     */
    private void assertRobotOnline(UUID robotId) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey("robot:" + robotId + ":online"))) {
            throw BusinessException.conflict("机器人离线，不能下发任务或即时指令");
        }
    }

    /**
     * Verifies that the robot has already completed the MQTT ONLINE authentication handshake.
     */
    private void assertRobotAuthenticated(RobotEntity robot) {
        String key = "robot:auth-session:" + robot.getManufacturer() + ":" + robot.getSerialNumber();
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw BusinessException.conflict("机器人未完成 MQTT 鉴权，不能下发任务或即时指令");
        }
    }

    /**
     * Requires a factsheet row when capability validation is enabled.
     */
    private RobotFactsheetEntity loadFactsheet(UUID robotId) {
        RobotFactsheetEntity factsheet = factsheetMapper.selectById(robotId);
        if (factsheet == null) {
            throw BusinessException.conflict("机器人尚未上报 factsheet，不能校验任务能力");
        }
        return factsheet;
    }

    /**
     * Validates the task shape first, then optionally checks factsheet capability constraints.
     */
    private void assertOrderSupported(UUID robotId, CreateOrderRequest request) {
        validateOrderTopology(request);
        if (!requireFactsheetCapabilities) {
            return;
        }
        RobotFactsheetEntity factsheet = loadFactsheet(robotId);
        assertMaximumSpeedSupported(factsheet, request);
        assertActionListSupported(factsheet, collectNodeActions(request), "NODE");
        assertActionListSupported(factsheet, collectEdgeActions(request), "EDGE");
    }

    /**
     * Validates a single instant action against the robot factsheet unless it is the bootstrap request.
     */
    private void assertInstantActionSupported(UUID robotId, String actionType) {
        if ("factsheetRequest".equals(actionType) || "cancelOrder".equals(actionType)) {
            return;
        }
        if (!requireFactsheetCapabilities) {
            return;
        }
        RobotFactsheetEntity factsheet = factsheetMapper.selectById(robotId);
        if (factsheet == null) {
            throw BusinessException.conflict("机器人尚未上报 factsheet，不能校验即时指令能力");
        }
        assertActionSupported(factsheet, actionType, "INSTANT");
    }

    /**
     * Ensures the task graph is a linear chain of adjacent nodes and matching edges.
     */
    private void validateOrderTopology(CreateOrderRequest request) {
        if (request.nodes() == null || request.nodes().isEmpty()) {
            throw BusinessException.conflict("任务至少需要一个节点");
        }
        int expectedEdges = Math.max(0, request.nodes().size() - 1);
        List<TaskEdgeRequest> edges = request.edges();
        if (edges != null && !edges.isEmpty() && edges.size() != expectedEdges) {
            throw BusinessException.conflict("edges 数量必须等于 nodes 数量减 1，或不传由平台自动生成");
        }
        for (int i = 1; i < request.nodes().size(); i++) {
            TaskEdgeRequest edge = edgeAt(edges, i - 1);
            if (edge == null) {
                continue;
            }
            String expectedStart = request.nodes().get(i - 1).nodeId();
            String expectedEnd = request.nodes().get(i).nodeId();
            if (!expectedStart.equals(edge.startNodeId()) || !expectedEnd.equals(edge.endNodeId())) {
                throw BusinessException.conflict("edges 必须按 nodes 顺序连接相邻节点");
            }
        }
    }

    /**
     * Rejects edges that request a maximum speed above the robot's declared capability.
     */
    private void assertMaximumSpeedSupported(RobotFactsheetEntity factsheet, CreateOrderRequest request) {
        Map<String, Object> rawPayload = factsheet.getRawPayload() == null ? Map.of() : factsheet.getRawPayload();
        Double supportedMaximumSpeed = numberValue(asMap(rawPayload.get("physicalParameters")).get("maximumSpeed"));
        if (supportedMaximumSpeed == null) {
            return;
        }
        int expectedEdges = Math.max(0, request.nodes().size() - 1);
        for (int i = 0; i < expectedEdges; i++) {
            double requestedMaximumSpeed = edgeMaximumSpeed(request, edgeAt(request.edges(), i));
            if (requestedMaximumSpeed > supportedMaximumSpeed) {
                throw BusinessException.conflict("任务边最大速度超过机器人 factsheet 声明能力");
            }
        }
    }

    /**
     * Collects every action declared on every node so they can be validated in one pass.
     */
    private List<Map<String, Object>> collectNodeActions(CreateOrderRequest request) {
        return request.nodes().stream()
            .flatMap(node -> defaultList(node.actions()).stream())
            .toList();
    }

    /**
     * Collects every action declared on every edge so they can be validated in one pass.
     */
    private List<Map<String, Object>> collectEdgeActions(CreateOrderRequest request) {
        if (request.edges() == null) {
            return List.of();
        }
        return request.edges().stream()
            .flatMap(edge -> defaultList(edge.actions()).stream())
            .toList();
    }

    /**
     * Checks every action in a list against the allowed actions for the requested scope.
     */
    private void assertActionListSupported(RobotFactsheetEntity factsheet, List<Map<String, Object>> actions, String scope) {
        for (Map<String, Object> action : actions) {
            String actionType = stringValue(action.get("actionType"));
            if (StringUtils.hasText(actionType)) {
                assertActionSupported(factsheet, actionType, scope);
            }
        }
    }

    /**
     * Fails closed when the factsheet does not declare the requested action for the target scope.
     */
    private void assertActionSupported(RobotFactsheetEntity factsheet, String actionType, String scope) {
        Set<String> supported = supportedActionTypes(factsheet, scope);
        if (supported == null || !supported.contains(actionType)) {
            throw BusinessException.conflict("机器人 factsheet 未声明支持指令：" + actionType);
        }
    }

    /**
     * Extracts the list of actions from factsheet JSON while honoring optional action scopes.
     */
    private Set<String> supportedActionTypes(RobotFactsheetEntity factsheet, String scope) {
        Map<String, Object> rawPayload = factsheet.getRawPayload();
        if (rawPayload == null) {
            return null;
        }
        Object actionValues = asMap(rawPayload.get("protocolFeatures")).get("mobileRobotActions");
        if (!(actionValues instanceof List<?> actions)) {
            return null;
        }

        Set<String> supported = new HashSet<>();
        for (Object value : actions) {
            Map<String, Object> action = asMap(value);
            String actionType = stringValue(action.get("actionType"));
            if (!StringUtils.hasText(actionType)) {
                continue;
            }
            Object scopeValues = action.get("actionScopes");
            if (!(scopeValues instanceof List<?> scopes) || scopes.isEmpty() || containsIgnoreCase(scopes, scope)) {
                supported.add(actionType);
            }
        }
        return supported;
    }

    /**
     * Performs a case-insensitive membership check for protocol scope names.
     */
    private boolean containsIgnoreCase(List<?> values, String target) {
        for (Object value : values) {
            if (target.equalsIgnoreCase(stringValue(value))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Safely views a raw object as a map when the payload already contains nested JSON.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    /**
     * Reads numeric values whether the factsheet encoded them as strings or JSON numbers.
     */
    private Double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Normalizes arbitrary action payload values into a string for comparison.
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Generates the MQTT headerId from a Redis counter so each outbound frame is unique.
     */
    private long nextHeaderId(UUID robotId) {
        Long value = stringRedisTemplate.opsForValue().increment("robot:header:" + robotId);
        return value == null ? 1L : value;
    }

    /**
     * Publishes the JSON payload to the robot-specific MQTT topic and maps transport failures to business errors.
     */
    private void publish(RobotEntity robot, String channel, Map<String, Object> payload) {
        if (!StringUtils.hasText(robot.getManufacturer()) || !StringUtils.hasText(robot.getSerialNumber())) {
            throw BusinessException.conflict("机器人缺少 manufacturer 或 serialNumber，无法构造 MQTT Topic");
        }
        String topic = "uagv/v3/" + robot.getManufacturer() + "/" + robot.getSerialNumber() + "/" + channel;
        try {
            mqttClientService.publishJson(topic, payload, 1);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        }
    }

    /**
     * Returns the edge at the requested index or null when edges were omitted and will be generated server-side.
     */
    private TaskEdgeRequest edgeAt(List<TaskEdgeRequest> edges, int index) {
        return edges == null || index >= edges.size() ? null : edges.get(index);
    }

    /**
     * Picks the most specific maximum speed available: edge override, task default, then 1.0.
     */
    private Double edgeMaximumSpeed(CreateOrderRequest request, TaskEdgeRequest edge) {
        if (edge != null && edge.maximumSpeed() != null) {
            return edge.maximumSpeed();
        }
        if (request.maximumSpeed() != null) {
            return request.maximumSpeed();
        }
        return 1.0;
    }

    /**
     * Turns null lists into empty immutable lists so downstream code can stream safely.
     */
    private List<Map<String, Object>> defaultList(List<Map<String, Object>> value) {
        return value == null ? List.of() : value;
    }

    /**
     * Adds an optional field only when the caller actually supplied a value.
     */
    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * Trims a string and falls back only when the source value is blank.
     */
    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
