package com.robotmanagement.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import com.robotmanagement.task.dto.CreateOrderRequest;
import com.robotmanagement.task.dto.InstantActionRequest;
import com.robotmanagement.task.dto.InstantActionResponse;
import com.robotmanagement.task.dto.OrderResponse;
import com.robotmanagement.task.dto.TaskNodeRequest;
import com.robotmanagement.task.entity.OrderEntity;
import com.robotmanagement.task.mapper.OrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final RobotMapper robotMapper;
    private final OperatorRobotAccessMapper accessMapper;

    public OrderService(
        OrderMapper orderMapper,
        RobotMapper robotMapper,
        OperatorRobotAccessMapper accessMapper
    ) {
        this.orderMapper = orderMapper;
        this.robotMapper = robotMapper;
        this.accessMapper = accessMapper;
    }

    @Transactional
    public OrderResponse createOrder(UUID robotId, CreateOrderRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser.isViewer()) {
            throw BusinessException.forbidden("viewer 不能下发任务");
        }

        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        String orderId = StringUtils.hasText(request.orderId())
            ? request.orderId().trim()
            : "order-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        OrderEntity order = new OrderEntity();
        order.setId(UUID.randomUUID());
        order.setTenantId(currentUser.tenantId());
        order.setRobotId(robotId);
        order.setOrderId(orderId);
        order.setStatus("pending");
        order.setPayload(buildOrderPayload(robot, orderId, request, now));
        order.setCreatedBy(currentUser.operatorId());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        // MQTT 发布将在 EMQX 配置完成后接入；当前先持久化 payload，便于前后端联调。
        return OrderResponse.from(order, robot);
    }

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

    public OrderResponse getOrder(UUID robotId, UUID orderRecordId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        OrderEntity order = orderMapper.selectById(orderRecordId);
        if (order == null || !robotId.equals(order.getRobotId()) || !currentUser.tenantId().equals(order.getTenantId())) {
            throw BusinessException.notFound("任务不存在");
        }
        return OrderResponse.from(order, robot);
    }

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

        order.setStatus("cancelled");
        order.setUpdatedAt(OffsetDateTime.now());
        orderMapper.updateById(order);
        return OrderResponse.from(order, robot);
    }

    public InstantActionResponse createInstantAction(UUID robotId, InstantActionRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser.isViewer()) {
            throw BusinessException.forbidden("viewer 不能下发即时指令");
        }

        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> payload = buildInstantActionPayload(robot, request, now);
        // MQTT 发布将在 EMQX 配置完成后接入。
        return new InstantActionResponse(robotId, request.actionType(), payload, now);
    }

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

    private Map<String, Object> buildOrderPayload(
        RobotEntity robot,
        String orderId,
        CreateOrderRequest request,
        OffsetDateTime now
    ) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        List<TaskNodeRequest> sourceNodes = request.nodes();

        for (int i = 0; i < sourceNodes.size(); i++) {
            TaskNodeRequest source = sourceNodes.get(i);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("nodeId", source.nodeId());
            node.put("sequenceId", i * 2L);
            node.put("released", true);
            node.put("nodePosition", Map.of(
                "x", source.x(),
                "y", source.y(),
                "theta", source.theta() == null ? 0.0 : source.theta()
            ));
            node.put("actions", List.of());
            nodes.add(node);

            if (i > 0) {
                TaskNodeRequest previous = sourceNodes.get(i - 1);
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("edgeId", previous.nodeId() + "-" + source.nodeId());
                edge.put("sequenceId", i * 2L - 1L);
                edge.put("released", true);
                edge.put("startNodeId", previous.nodeId());
                edge.put("endNodeId", source.nodeId());
                edge.put("maxSpeed", request.maxSpeed() == null ? 1.0 : request.maxSpeed());
                edge.put("actions", List.of());
                edges.add(edge);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headerId", 0);
        payload.put("timestamp", now.toString());
        payload.put("version", "3.0.0");
        payload.put("manufacturer", robot.getManufacturer());
        payload.put("serialNumber", robot.getSerialNumber());
        payload.put("orderId", orderId);
        payload.put("orderUpdateId", 0);
        payload.put("nodes", nodes);
        payload.put("edges", edges);
        return payload;
    }

    private Map<String, Object> buildInstantActionPayload(
        RobotEntity robot,
        InstantActionRequest request,
        OffsetDateTime now
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
        payload.put("headerId", 0);
        payload.put("timestamp", now.toString());
        payload.put("version", "3.0.0");
        payload.put("manufacturer", robot.getManufacturer());
        payload.put("serialNumber", robot.getSerialNumber());
        payload.put("actions", List.of(action));
        return payload;
    }
}
