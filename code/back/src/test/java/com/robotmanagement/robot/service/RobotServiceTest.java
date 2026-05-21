package com.robotmanagement.robot.service;

import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.dto.RobotResponse;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RobotServiceTest {

    private final RobotMapper robotMapper = mock(RobotMapper.class);
    private final OperatorRobotAccessMapper accessMapper = mock(OperatorRobotAccessMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID robotId = UUID.randomUUID();

    private RobotService robotService;

    @BeforeEach
    void setUp() {
        robotService = new RobotService(robotMapper, accessMapper, redisTemplate);
        authenticate("admin", adminId, "admin");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRobotReturnsOnlineFlagFromRedis() {
        when(robotMapper.selectById(robotId)).thenReturn(activeRobot(tenantId));
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(true);

        RobotResponse response = robotService.getRobot(robotId);

        assertThat(response.id()).isEqualTo(robotId);
        assertThat(response.serialNumber()).isEqualTo("SN-2001");
        assertThat(response.online()).isTrue();
    }

    @Test
    void getRobotRejectsRobotOutsideTenant() {
        when(robotMapper.selectById(robotId)).thenReturn(activeRobot(UUID.randomUUID()));

        assertThatThrownBy(() -> robotService.getRobot(robotId))
            .isInstanceOf(BusinessException.class);
    }

    private RobotEntity activeRobot(UUID ownerTenantId) {
        RobotEntity robot = new RobotEntity();
        robot.setId(robotId);
        robot.setTenantId(ownerTenantId);
        robot.setExternalRobotId("AUTH-RBT-2001");
        robot.setSerialNumber("SN-2001");
        robot.setManufacturer("JSYS");
        robot.setStatus("active");
        return robot;
    }

    private void authenticate(String username, UUID operatorId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CurrentUser(operatorId, tenantId, username, role, "Default Tenant", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            )
        );
    }
}
