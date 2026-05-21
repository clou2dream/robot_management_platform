package com.robotmanagement.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.dto.RobotResponse;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RobotService {

    private final RobotMapper robotMapper;
    private final OperatorRobotAccessMapper accessMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public RobotService(
        RobotMapper robotMapper,
        OperatorRobotAccessMapper accessMapper,
        StringRedisTemplate stringRedisTemplate
    ) {
        this.robotMapper = robotMapper;
        this.accessMapper = accessMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public PageResult<RobotResponse> listRobots(long page, long size, String keyword, String status) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        LambdaQueryWrapper<RobotEntity> wrapper = new LambdaQueryWrapper<RobotEntity>()
            .eq(RobotEntity::getTenantId, currentUser.tenantId())
            .orderByDesc(RobotEntity::getSyncedAt);

        if (StringUtils.hasText(keyword)) {
            String like = keyword.trim();
            wrapper.and(query -> query
                .like(RobotEntity::getSerialNumber, like)
                .or()
                .like(RobotEntity::getManufacturer, like)
                .or()
                .like(RobotEntity::getExternalRobotId, like)
            );
        }

        if (StringUtils.hasText(status)) {
            wrapper.eq(RobotEntity::getStatus, normalizeStatus(status));
        }

        if (!currentUser.isAdmin()) {
            List<UUID> allowedRobotIds = accessMapper.selectList(
                new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                    .eq(OperatorRobotAccessEntity::getOperatorId, currentUser.operatorId())
            ).stream().map(OperatorRobotAccessEntity::getRobotId).toList();

            if (allowedRobotIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, page, size);
            }
            wrapper.in(RobotEntity::getId, allowedRobotIds);
        }

        Page<RobotEntity> result = robotMapper.selectPage(new Page<>(page, size), wrapper);
        List<RobotResponse> records = result.getRecords()
            .stream()
            .map(entity -> RobotResponse.from(entity, isOnline(entity.getId())))
            .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public RobotResponse getRobot(UUID robotId) {
        RobotEntity robot = loadAccessibleRobot(robotId);
        return RobotResponse.from(robot, isOnline(robot.getId()));
    }

    private RobotEntity loadAccessibleRobot(UUID robotId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
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
            throw BusinessException.forbidden("无权访问该机器人");
        }

        return robot;
    }

    private boolean isOnline(UUID robotId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey("robot:" + robotId + ":online"));
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "active";
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "active", "disabled", "revoked" -> normalized;
            default -> "active";
        };
    }

}
