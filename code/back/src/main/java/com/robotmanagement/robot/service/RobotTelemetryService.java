package com.robotmanagement.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.operator.entity.OperatorEntity;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorMapper;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.realtime.RealtimeMessagePublisher;
import com.robotmanagement.robot.dto.RobotConnectionResponse;
import com.robotmanagement.robot.dto.RobotFactsheetResponse;
import com.robotmanagement.robot.dto.RobotPositionResponse;
import com.robotmanagement.robot.dto.RobotRealtimeResponse;
import com.robotmanagement.robot.dto.RobotResponse;
import com.robotmanagement.robot.dto.RobotStateResponse;
import com.robotmanagement.robot.entity.RobotConnectionEntity;
import com.robotmanagement.robot.entity.RobotFactsheetEntity;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.entity.RobotPositionEntity;
import com.robotmanagement.robot.entity.RobotStateEntity;
import com.robotmanagement.robot.mapper.RobotConnectionMapper;
import com.robotmanagement.robot.mapper.RobotFactsheetMapper;
import com.robotmanagement.robot.mapper.RobotMapper;
import com.robotmanagement.robot.mapper.RobotPositionMapper;
import com.robotmanagement.robot.mapper.RobotStateMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RobotTelemetryService {

    private final RobotMapper robotMapper;
    private final OperatorMapper operatorMapper;
    private final OperatorRobotAccessMapper accessMapper;
    private final RobotConnectionMapper connectionMapper;
    private final RobotStateMapper stateMapper;
    private final RobotPositionMapper positionMapper;
    private final RobotFactsheetMapper factsheetMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public RobotTelemetryService(
        RobotMapper robotMapper,
        OperatorMapper operatorMapper,
        OperatorRobotAccessMapper accessMapper,
        RobotConnectionMapper connectionMapper,
        RobotStateMapper stateMapper,
        RobotPositionMapper positionMapper,
        RobotFactsheetMapper factsheetMapper,
        StringRedisTemplate stringRedisTemplate
    ) {
        this.robotMapper = robotMapper;
        this.operatorMapper = operatorMapper;
        this.accessMapper = accessMapper;
        this.connectionMapper = connectionMapper;
        this.stateMapper = stateMapper;
        this.positionMapper = positionMapper;
        this.factsheetMapper = factsheetMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public RobotRealtimeResponse getRealtime(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        RobotEntity robot = loadAccessibleRobot(robotId, currentUser);
        RobotConnectionEntity connection = latestConnection(currentUser.tenantId(), robotId);
        RobotStateEntity state = latestState(currentUser.tenantId(), robotId);
        RobotPositionEntity position = latestPosition(currentUser.tenantId(), robotId);
        boolean includeDebugPayload = hasDebugPermission(currentUser);

        boolean online = isOnline(robotId);
        RobotConnectionResponse connectionResponse = RobotConnectionResponse.from(connection, online);
        return new RobotRealtimeResponse(
            RobotResponse.from(robot, online),
            connectionResponse,
            RobotStateResponse.from(state, includeDebugPayload),
            RobotPositionResponse.from(position)
        );
    }

    public RobotConnectionResponse getConnection(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        loadAccessibleRobot(robotId, currentUser);
        return RobotConnectionResponse.from(latestConnection(currentUser.tenantId(), robotId), isOnline(robotId));
    }

    public RobotStateResponse getState(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        loadAccessibleRobot(robotId, currentUser);
        return RobotStateResponse.from(latestState(currentUser.tenantId(), robotId), hasDebugPermission(currentUser));
    }

    public RobotPositionResponse getPosition(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        loadAccessibleRobot(robotId, currentUser);
        return RobotPositionResponse.from(latestPosition(currentUser.tenantId(), robotId));
    }

    public List<RobotPositionResponse> getTrajectory(UUID robotId, int limit) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        loadAccessibleRobot(robotId, currentUser);
        int safeLimit = sanitizeTrajectoryLimit(limit);

        return positionMapper.selectList(
                new LambdaQueryWrapper<RobotPositionEntity>()
                    .eq(RobotPositionEntity::getTenantId, currentUser.tenantId())
                    .eq(RobotPositionEntity::getRobotId, robotId)
                    .isNotNull(RobotPositionEntity::getPosX)
                    .isNotNull(RobotPositionEntity::getPosY)
                    .orderByDesc(RobotPositionEntity::getTime)
                    .last("limit " + safeLimit)
            )
            .stream()
            .map(RobotPositionResponse::from)
            .sorted(Comparator.comparing(RobotPositionResponse::time, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    public RobotFactsheetResponse getFactsheet(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        loadAccessibleRobot(robotId, currentUser);
        return RobotFactsheetResponse.from(latestFactsheet(currentUser.tenantId(), robotId), hasDebugPermission(currentUser));
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

    private RobotFactsheetEntity latestFactsheet(UUID tenantId, UUID robotId) {
        RobotFactsheetEntity factsheet = factsheetMapper.selectById(robotId);
        if (factsheet == null || !tenantId.equals(factsheet.getTenantId())) {
            return null;
        }
        return factsheet;
    }

    /**
     * Uses the same Redis online marker as the robot list so all frontend views agree on online state.
     */
    private boolean isOnline(UUID robotId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey("robot:" + robotId + ":online"));
    }

    private boolean hasDebugPermission(CurrentUser currentUser) {
        OperatorEntity operator = operatorMapper.selectById(currentUser.operatorId());
        return operator != null
            && currentUser.tenantId().equals(operator.getTenantId())
            && Boolean.TRUE.equals(operator.getDebugPermission());
    }

    private int sanitizeTrajectoryLimit(int limit) {
        if (limit <= 0) {
            return 240;
        }
        return Math.min(limit, 2000);
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

}
