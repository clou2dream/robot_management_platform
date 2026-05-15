package com.robotmanagement.operator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.operator.dto.CreateOperatorRequest;
import com.robotmanagement.operator.dto.GrantRobotAccessRequest;
import com.robotmanagement.operator.dto.OperatorResponse;
import com.robotmanagement.operator.dto.UpdateOperatorRoleRequest;
import com.robotmanagement.operator.entity.OperatorEntity;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorMapper;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.dto.RobotResponse;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OperatorService {

    private final OperatorMapper operatorMapper;
    private final OperatorRobotAccessMapper accessMapper;
    private final RobotMapper robotMapper;
    private final PasswordEncoder passwordEncoder;

    public OperatorService(
        OperatorMapper operatorMapper,
        OperatorRobotAccessMapper accessMapper,
        RobotMapper robotMapper,
        PasswordEncoder passwordEncoder
    ) {
        this.operatorMapper = operatorMapper;
        this.accessMapper = accessMapper;
        this.robotMapper = robotMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<OperatorResponse> listOperators(long page, long size, String keyword) {
        CurrentUser currentUser = requireAdmin();
        LambdaQueryWrapper<OperatorEntity> wrapper = new LambdaQueryWrapper<OperatorEntity>()
            .eq(OperatorEntity::getTenantId, currentUser.tenantId())
            .orderByDesc(OperatorEntity::getCreatedAt);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(OperatorEntity::getUsername, keyword.trim());
        }

        Page<OperatorEntity> result = operatorMapper.selectPage(new Page<>(page, size), wrapper);
        List<OperatorResponse> records = result.getRecords()
            .stream()
            .map(entity -> OperatorResponse.from(entity, countRobotAccess(entity.getId())))
            .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public OperatorResponse createOperator(CreateOperatorRequest request) {
        CurrentUser currentUser = requireAdmin();
        String username = request.username().trim();
        long exists = operatorMapper.selectCount(
            new LambdaQueryWrapper<OperatorEntity>()
                .eq(OperatorEntity::getTenantId, currentUser.tenantId())
                .eq(OperatorEntity::getUsername, username)
        );
        if (exists > 0) {
            throw BusinessException.conflict("用户名已存在");
        }

        OperatorEntity operator = new OperatorEntity();
        operator.setId(UUID.randomUUID());
        operator.setTenantId(currentUser.tenantId());
        operator.setUsername(username);
        operator.setPasswordHash(passwordEncoder.encode(request.password()));
        operator.setRole(request.role());
        operator.setPasswordResetRequired(false);
        operator.setCreatedAt(OffsetDateTime.now());
        operatorMapper.insert(operator);
        return OperatorResponse.from(operator, 0);
    }

    @Transactional
    public OperatorResponse updateRole(UUID operatorId, UpdateOperatorRoleRequest request) {
        requireAdmin();
        OperatorEntity operator = loadOperatorInTenant(operatorId);
        operator.setRole(request.role());
        operatorMapper.updateById(operator);
        return OperatorResponse.from(operator, countRobotAccess(operator.getId()));
    }

    @Transactional
    public void deleteOperator(UUID operatorId) {
        CurrentUser currentUser = requireAdmin();
        if (currentUser.operatorId().equals(operatorId)) {
            throw BusinessException.conflict("不能删除当前登录账号");
        }
        OperatorEntity operator = loadOperatorInTenant(operatorId);
        operatorMapper.deleteById(operator.getId());
    }

    public List<RobotResponse> listRobotAccess(UUID operatorId) {
        requireAdmin();
        OperatorEntity operator = loadOperatorInTenant(operatorId);
        List<UUID> robotIds = accessMapper.selectList(
            new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                .eq(OperatorRobotAccessEntity::getOperatorId, operator.getId())
        ).stream().map(OperatorRobotAccessEntity::getRobotId).toList();

        if (robotIds.isEmpty()) {
            return List.of();
        }

        return robotMapper.selectList(
            new LambdaQueryWrapper<RobotEntity>()
                .eq(RobotEntity::getTenantId, operator.getTenantId())
                .in(RobotEntity::getId, robotIds)
        ).stream().map(robot -> RobotResponse.from(robot, false)).toList();
    }

    @Transactional
    public void grantRobotAccess(UUID operatorId, GrantRobotAccessRequest request) {
        requireAdmin();
        OperatorEntity operator = loadOperatorInTenant(operatorId);
        if ("admin".equals(operator.getRole())) {
            throw BusinessException.conflict("admin 默认拥有全部机器人权限，无需单独授权");
        }

        for (UUID robotId : request.robotIds()) {
            RobotEntity robot = robotMapper.selectById(robotId);
            if (robot == null || !operator.getTenantId().equals(robot.getTenantId())) {
                throw BusinessException.notFound("机器人不存在：" + robotId);
            }

            Long exists = accessMapper.selectCount(
                new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                    .eq(OperatorRobotAccessEntity::getOperatorId, operatorId)
                    .eq(OperatorRobotAccessEntity::getRobotId, robotId)
            );
            if (exists != null && exists > 0) {
                continue;
            }

            OperatorRobotAccessEntity access = new OperatorRobotAccessEntity();
            access.setOperatorId(operatorId);
            access.setRobotId(robotId);
            access.setGrantedAt(OffsetDateTime.now());
            accessMapper.insert(access);
        }
    }

    @Transactional
    public void revokeRobotAccess(UUID operatorId, UUID robotId) {
        requireAdmin();
        loadOperatorInTenant(operatorId);
        accessMapper.delete(
            new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                .eq(OperatorRobotAccessEntity::getOperatorId, operatorId)
                .eq(OperatorRobotAccessEntity::getRobotId, robotId)
        );
    }

    private CurrentUser requireAdmin() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (!currentUser.isAdmin()) {
            throw BusinessException.forbidden("只有 admin 可以管理运营人员");
        }
        return currentUser;
    }

    private OperatorEntity loadOperatorInTenant(UUID operatorId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        OperatorEntity operator = operatorMapper.selectById(operatorId);
        if (operator == null || !currentUser.tenantId().equals(operator.getTenantId())) {
            throw BusinessException.notFound("运营人员不存在");
        }
        return operator;
    }

    private long countRobotAccess(UUID operatorId) {
        Long count = accessMapper.selectCount(
            new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                .eq(OperatorRobotAccessEntity::getOperatorId, operatorId)
        );
        return count == null ? 0 : count;
    }
}
