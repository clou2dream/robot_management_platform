package com.robotmanagement.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.dto.AuthorizedRobotSyncItem;
import com.robotmanagement.robot.dto.RobotResponse;
import com.robotmanagement.robot.dto.RobotSyncRequest;
import com.robotmanagement.robot.dto.RobotSyncResult;
import com.robotmanagement.robot.dto.RobotSyncStatusResponse;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
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

    public RobotSyncStatusResponse getSyncStatus(UUID robotId) {
        RobotEntity robot = loadAccessibleRobot(robotId);
        return new RobotSyncStatusResponse(
            robot.getId(),
            robot.getExternalRobotId(),
            robot.getSerialNumber(),
            robot.getStatus(),
            robot.getSyncedAt()
        );
    }

    @Transactional
    public RobotSyncResult syncRobots(RobotSyncRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有 admin 可以同步机器人");
        }

        List<AuthorizedRobotSyncItem> sourceItems = request == null || request.robots() == null || request.robots().isEmpty()
            ? defaultDemoRobots()
            : request.robots();

        int created = 0;
        int updated = 0;
        int skipped = 0;
        OffsetDateTime now = OffsetDateTime.now();

        for (AuthorizedRobotSyncItem item : sourceItems) {
            if (!StringUtils.hasText(item.externalRobotId()) || !StringUtils.hasText(item.serialNumber())) {
                skipped++;
                continue;
            }

            RobotEntity existing = robotMapper.selectOne(
                new LambdaQueryWrapper<RobotEntity>()
                    .eq(RobotEntity::getTenantId, currentUser.tenantId())
                    .eq(RobotEntity::getExternalRobotId, item.externalRobotId())
            );

            if (existing == null) {
                RobotEntity robot = new RobotEntity();
                robot.setId(UUID.randomUUID());
                robot.setTenantId(currentUser.tenantId());
                robot.setExternalRobotId(item.externalRobotId());
                robot.setSerialNumber(item.serialNumber());
                robot.setManufacturer(defaultString(item.manufacturer(), "UNKNOWN"));
                robot.setSourceAppId(item.sourceAppId());
                robot.setStatus(normalizeStatus(item.status()));
                robot.setSyncedAt(now);
                robot.setCreatedAt(now);
                robot.setUpdatedAt(now);
                robotMapper.insert(robot);
                created++;
            } else {
                existing.setSerialNumber(item.serialNumber());
                existing.setManufacturer(defaultString(item.manufacturer(), existing.getManufacturer()));
                existing.setSourceAppId(item.sourceAppId());
                existing.setStatus(normalizeStatus(item.status()));
                existing.setSyncedAt(now);
                existing.setUpdatedAt(now);
                robotMapper.updateById(existing);
                updated++;
            }
        }

        return new RobotSyncResult(created, updated, skipped);
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

    private List<AuthorizedRobotSyncItem> defaultDemoRobots() {
        return List.of(
            new AuthorizedRobotSyncItem("AUTH-RBT-1001", "SN-001", "JSYS", null, "active"),
            new AuthorizedRobotSyncItem("AUTH-RBT-1002", "SN-002", "JSYS", null, "active"),
            new AuthorizedRobotSyncItem("AUTH-RBT-1003", "SN-003", "CHAMELEON", null, "disabled")
        );
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

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
