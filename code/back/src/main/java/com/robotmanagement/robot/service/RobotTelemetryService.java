package com.robotmanagement.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.dto.CreateDemoTelemetryRequest;
import com.robotmanagement.robot.dto.RobotConnectionResponse;
import com.robotmanagement.robot.dto.RobotPositionResponse;
import com.robotmanagement.robot.dto.RobotRealtimeResponse;
import com.robotmanagement.robot.dto.RobotResponse;
import com.robotmanagement.robot.dto.RobotStateResponse;
import com.robotmanagement.robot.entity.RobotConnectionEntity;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.entity.RobotPositionEntity;
import com.robotmanagement.robot.entity.RobotStateEntity;
import com.robotmanagement.robot.mapper.RobotConnectionMapper;
import com.robotmanagement.robot.mapper.RobotMapper;
import com.robotmanagement.robot.mapper.RobotPositionMapper;
import com.robotmanagement.robot.mapper.RobotStateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RobotTelemetryService {

    private final RobotMapper robotMapper;
    private final OperatorRobotAccessMapper accessMapper;
    private final RobotConnectionMapper connectionMapper;
    private final RobotStateMapper stateMapper;
    private final RobotPositionMapper positionMapper;

    public RobotTelemetryService(
        RobotMapper robotMapper,
        OperatorRobotAccessMapper accessMapper,
        RobotConnectionMapper connectionMapper,
        RobotStateMapper stateMapper,
        RobotPositionMapper positionMapper
    ) {
        this.robotMapper = robotMapper;
        this.accessMapper = accessMapper;
        this.connectionMapper = connectionMapper;
        this.stateMapper = stateMapper;
        this.positionMapper = positionMapper;
    }

    public RobotRealtimeResponse getRealtime(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        RobotConnectionEntity connection = latestConnection(currentUser.tenantId(), robotId);
        RobotStateEntity state = latestState(currentUser.tenantId(), robotId);
        RobotPositionEntity position = latestPosition(currentUser.tenantId(), robotId);

        RobotConnectionResponse connectionResponse = RobotConnectionResponse.from(connection);
        boolean online = connectionResponse != null && connectionResponse.online();
        return new RobotRealtimeResponse(
            RobotResponse.from(robot, online),
            connectionResponse,
            RobotStateResponse.from(state),
            RobotPositionResponse.from(position)
        );
    }

    public RobotConnectionResponse getConnection(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        loadAccessibleRobot(robotId, currentUser);
        return RobotConnectionResponse.from(latestConnection(currentUser.tenantId(), robotId));
    }

    public RobotStateResponse getState(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        loadAccessibleRobot(robotId, currentUser);
        return RobotStateResponse.from(latestState(currentUser.tenantId(), robotId));
    }

    public RobotPositionResponse getPosition(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        loadAccessibleRobot(robotId, currentUser);
        return RobotPositionResponse.from(latestPosition(currentUser.tenantId(), robotId));
    }

    @Transactional
    public RobotRealtimeResponse createDemoTelemetry(UUID robotId, CreateDemoTelemetryRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser.isViewer()) {
            throw BusinessException.forbidden("viewer 不能生成演示状态");
        }

        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        OffsetDateTime now = OffsetDateTime.now();
        double x = defaultDouble(request == null ? null : request.x(), random(10, 90));
        double y = defaultDouble(request == null ? null : request.y(), random(10, 90));
        double theta = defaultDouble(request == null ? null : request.theta(), random(-3.14, 3.14));
        String mapId = defaultString(request == null ? null : request.mapId(), "demo-map");
        String operatingMode = defaultString(request == null ? null : request.operatingMode(), "AUTO");
        String orderId = defaultString(request == null ? null : request.orderId(), "order-demo");
        short batterySoc = normalizeBattery(request == null ? null : request.batterySoc());

        RobotConnectionEntity connection = new RobotConnectionEntity();
        connection.setTime(now);
        connection.setTenantId(currentUser.tenantId());
        connection.setRobotId(robotId);
        connection.setConnectionState(defaultString(request == null ? null : request.connectionState(), "online"));
        connectionMapper.insert(connection);

        RobotStateEntity state = new RobotStateEntity();
        state.setTime(now);
        state.setTenantId(currentUser.tenantId());
        state.setRobotId(robotId);
        state.setPosX(x);
        state.setPosY(y);
        state.setPosTheta(theta);
        state.setMapId(mapId);
        state.setBatterySoc(batterySoc);
        state.setOperatingMode(operatingMode);
        state.setOrderId(orderId);
        state.setRawPayload(buildDemoPayload(robot, x, y, theta, mapId, batterySoc, operatingMode, orderId, now));
        stateMapper.insert(state);

        RobotPositionEntity position = new RobotPositionEntity();
        position.setTime(now);
        position.setTenantId(currentUser.tenantId());
        position.setRobotId(robotId);
        position.setPosX(x);
        position.setPosY(y);
        position.setPosTheta(theta);
        position.setMapId(mapId);
        positionMapper.insert(position);

        return getRealtime(robotId);
    }

    private RobotConnectionEntity latestConnection(UUID tenantId, UUID robotId) {
        return connectionMapper.selectOne(
            new LambdaQueryWrapper<RobotConnectionEntity>()
                .eq(RobotConnectionEntity::getTenantId, tenantId)
                .eq(RobotConnectionEntity::getRobotId, robotId)
                .orderByDesc(RobotConnectionEntity::getTime)
                .last("limit 1")
        );
    }

    private RobotStateEntity latestState(UUID tenantId, UUID robotId) {
        return stateMapper.selectOne(
            new LambdaQueryWrapper<RobotStateEntity>()
                .eq(RobotStateEntity::getTenantId, tenantId)
                .eq(RobotStateEntity::getRobotId, robotId)
                .orderByDesc(RobotStateEntity::getTime)
                .last("limit 1")
        );
    }

    private RobotPositionEntity latestPosition(UUID tenantId, UUID robotId) {
        return positionMapper.selectOne(
            new LambdaQueryWrapper<RobotPositionEntity>()
                .eq(RobotPositionEntity::getTenantId, tenantId)
                .eq(RobotPositionEntity::getRobotId, robotId)
                .orderByDesc(RobotPositionEntity::getTime)
                .last("limit 1")
        );
    }

    private RobotEntity loadAccessibleRobot(UUID robotId, CurrentUser currentUser) {
        RobotEntity robot = robotMapper.selectById(robotId);
        if (robot == null || !currentUser.tenantId().equals(robot.getTenantId())) {
            throw BusinessException.notFound("机器人不存在");
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
            throw BusinessException.forbidden("无权访问该机器人状态");
        }
        return robot;
    }

    private Map<String, Object> buildDemoPayload(
        RobotEntity robot,
        double x,
        double y,
        double theta,
        String mapId,
        short batterySoc,
        String operatingMode,
        String orderId,
        OffsetDateTime now
    ) {
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("x", x);
        position.put("y", y);
        position.put("theta", theta);
        position.put("mapId", mapId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "demo");
        payload.put("timestamp", now.toString());
        payload.put("serialNumber", robot.getSerialNumber());
        payload.put("batterySoc", batterySoc);
        payload.put("operatingMode", operatingMode);
        payload.put("orderId", orderId);
        payload.put("position", position);
        return payload;
    }

    private short normalizeBattery(Integer value) {
        if (value == null) {
            return (short) ThreadLocalRandom.current().nextInt(45, 96);
        }
        return (short) Math.max(0, Math.min(100, value));
    }

    private double defaultDouble(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private double random(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
