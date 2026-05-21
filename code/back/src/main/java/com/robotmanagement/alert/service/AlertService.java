package com.robotmanagement.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.robotmanagement.alert.dto.AlertResponse;
import com.robotmanagement.alert.dto.AlertSummaryResponse;
import com.robotmanagement.alert.entity.AlertEntity;
import com.robotmanagement.alert.mapper.AlertMapper;
import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.realtime.RealtimeMessagePublisher;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private final AlertMapper alertMapper;
    private final RobotMapper robotMapper;
    private final OperatorRobotAccessMapper accessMapper;
    private final RealtimeMessagePublisher realtimeMessagePublisher;

    public AlertService(
        AlertMapper alertMapper,
        RobotMapper robotMapper,
        OperatorRobotAccessMapper accessMapper,
        RealtimeMessagePublisher realtimeMessagePublisher
    ) {
        this.alertMapper = alertMapper;
        this.robotMapper = robotMapper;
        this.accessMapper = accessMapper;
        this.realtimeMessagePublisher = realtimeMessagePublisher;
    }

    public PageResult<AlertResponse> listAlerts(
        long page,
        long size,
        String status,
        String level,
        UUID robotId,
        String keyword
    ) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        LambdaQueryWrapper<AlertEntity> wrapper = baseAccessibleWrapper(currentUser);
        applyQueryFilters(wrapper, status, level, robotId, keyword, currentUser);
        wrapper.orderByDesc(AlertEntity::getTriggeredAt);

        Page<AlertEntity> result = alertMapper.selectPage(new Page<>(page, size), wrapper);
        Map<UUID, RobotEntity> robots = loadRobotMap(result.getRecords());
        List<AlertResponse> records = result.getRecords()
            .stream()
            .map(alert -> AlertResponse.from(alert, robots.get(alert.getRobotId())))
            .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public AlertResponse getAlert(UUID alertId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        AlertEntity alert = loadAlertInTenant(alertId, currentUser);
        RobotEntity robot = loadRobotIfAccessible(alert.getRobotId(), currentUser);
        return AlertResponse.from(alert, robot);
    }

    public AlertSummaryResponse getSummary() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        List<AlertEntity> alerts = alertMapper.selectList(baseAccessibleWrapper(currentUser));
        long openTotal = alerts.stream().filter(alert -> alert.getResolvedAt() == null).count();
        long resolvedTotal = alerts.stream().filter(alert -> alert.getResolvedAt() != null).count();
        long warning = countOpenLevel(alerts, "WARNING");
        long urgent = countOpenLevel(alerts, "URGENT");
        long critical = countOpenLevel(alerts, "CRITICAL");
        long fatal = countOpenLevel(alerts, "FATAL");
        return new AlertSummaryResponse(openTotal, resolvedTotal, warning, urgent, critical, fatal);
    }

    @Transactional
    public AlertResponse resolveAlert(UUID alertId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser.isViewer()) {
            throw BusinessException.forbidden("viewer 不能处理告警");
        }

        AlertEntity alert = loadAlertInTenant(alertId, currentUser);
        if (alert.getResolvedAt() == null) {
            alert.setResolvedAt(OffsetDateTime.now());
            alertMapper.updateById(alert);
        }

        RobotEntity robot = loadRobotIfAccessible(alert.getRobotId(), currentUser);
        AlertResponse response = AlertResponse.from(alert, robot);
        realtimeMessagePublisher.alert(response);
        return response;
    }

    @Transactional
    public AlertResponse raiseSystemAlert(
        UUID tenantId,
        UUID robotId,
        String level,
        String errorType,
        String description,
        String hint,
        Map<String, Object> rawError
    ) {
        AlertEntity existing = alertMapper.selectOne(
            new LambdaQueryWrapper<AlertEntity>()
                .eq(AlertEntity::getTenantId, tenantId)
                .eq(AlertEntity::getRobotId, robotId)
                .eq(AlertEntity::getErrorType, errorType)
                .isNull(AlertEntity::getResolvedAt)
                .last("limit 1")
        );
        RobotEntity robot = robotId == null ? null : robotMapper.selectById(robotId);
        if (existing != null) {
            return AlertResponse.from(existing, robot);
        }

        AlertEntity alert = new AlertEntity();
        alert.setId(UUID.randomUUID());
        alert.setTenantId(tenantId);
        alert.setRobotId(robotId);
        alert.setTriggeredAt(OffsetDateTime.now());
        alert.setLevel(normalizeLevel(level));
        alert.setErrorType(errorType);
        alert.setDescription(description);
        alert.setHint(hint);
        alert.setRawError(rawError == null ? Map.of() : rawError);
        alertMapper.insert(alert);

        AlertResponse response = AlertResponse.from(alert, robot);
        realtimeMessagePublisher.alert(response);
        return response;
    }

    @Transactional
    public void resolveSystemAlert(UUID tenantId, UUID robotId, String errorType) {
        List<AlertEntity> alerts = alertMapper.selectList(
            new LambdaQueryWrapper<AlertEntity>()
                .eq(AlertEntity::getTenantId, tenantId)
                .eq(AlertEntity::getRobotId, robotId)
                .eq(AlertEntity::getErrorType, errorType)
                .isNull(AlertEntity::getResolvedAt)
        );
        OffsetDateTime now = OffsetDateTime.now();
        for (AlertEntity alert : alerts) {
            alert.setResolvedAt(now);
            alertMapper.updateById(alert);
            realtimeMessagePublisher.alert(AlertResponse.from(alert, robotMapper.selectById(robotId)));
        }
    }

    private LambdaQueryWrapper<AlertEntity> baseAccessibleWrapper(CurrentUser currentUser) {
        LambdaQueryWrapper<AlertEntity> wrapper = new LambdaQueryWrapper<AlertEntity>()
            .eq(AlertEntity::getTenantId, currentUser.tenantId());

        if (currentUser.isAdmin()) {
            return wrapper;
        }

        List<UUID> allowedRobotIds = listAllowedRobotIds(currentUser);
        if (allowedRobotIds.isEmpty()) {
            wrapper.isNull(AlertEntity::getRobotId);
        } else {
            wrapper.and(query -> query
                .isNull(AlertEntity::getRobotId)
                .or()
                .in(AlertEntity::getRobotId, allowedRobotIds)
            );
        }
        return wrapper;
    }

    private void applyQueryFilters(
        LambdaQueryWrapper<AlertEntity> wrapper,
        String status,
        String level,
        UUID robotId,
        String keyword,
        CurrentUser currentUser
    ) {
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "open" -> wrapper.isNull(AlertEntity::getResolvedAt);
                case "resolved" -> wrapper.isNotNull(AlertEntity::getResolvedAt);
                default -> throw BusinessException.conflict("不支持的告警状态：" + status);
            }
        }

        if (StringUtils.hasText(level)) {
            wrapper.eq(AlertEntity::getLevel, normalizeLevel(level));
        }

        if (robotId != null) {
            loadRobotIfAccessible(robotId, currentUser);
            wrapper.eq(AlertEntity::getRobotId, robotId);
        }

        if (StringUtils.hasText(keyword)) {
            String like = keyword.trim();
            wrapper.and(query -> query
                .like(AlertEntity::getErrorType, like)
                .or()
                .like(AlertEntity::getDescription, like)
                .or()
                .like(AlertEntity::getHint, like)
            );
        }
    }

    private AlertEntity loadAlertInTenant(UUID alertId, CurrentUser currentUser) {
        AlertEntity alert = alertMapper.selectById(alertId);
        if (alert == null || !currentUser.tenantId().equals(alert.getTenantId())) {
            throw BusinessException.notFound("告警不存在");
        }
        loadRobotIfAccessible(alert.getRobotId(), currentUser);
        return alert;
    }

    private RobotEntity loadRobotIfAccessible(UUID robotId, CurrentUser currentUser) {
        if (robotId == null) {
            return null;
        }

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
            throw BusinessException.forbidden("无权访问该机器人告警");
        }
        return robot;
    }

    private List<UUID> listAllowedRobotIds(CurrentUser currentUser) {
        return accessMapper.selectList(
            new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                .eq(OperatorRobotAccessEntity::getOperatorId, currentUser.operatorId())
        ).stream().map(OperatorRobotAccessEntity::getRobotId).toList();
    }

    private Map<UUID, RobotEntity> loadRobotMap(List<AlertEntity> alerts) {
        List<UUID> robotIds = alerts.stream()
            .map(AlertEntity::getRobotId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (robotIds.isEmpty()) {
            return Map.of();
        }

        return robotMapper.selectBatchIds(robotIds)
            .stream()
            .collect(Collectors.toMap(RobotEntity::getId, Function.identity()));
    }

    private long countOpenLevel(List<AlertEntity> alerts, String level) {
        return alerts.stream()
            .filter(alert -> alert.getResolvedAt() == null)
            .filter(alert -> level.equals(alert.getLevel()))
            .count();
    }

    private String normalizeLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return "WARNING";
        }

        String normalized = level.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WARNING", "URGENT", "CRITICAL", "FATAL" -> normalized;
            default -> throw BusinessException.conflict("不支持的告警等级：" + level);
        };
    }

}
