package com.robotmanagement.robot.service;

import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.operator.entity.OperatorEntity;
import com.robotmanagement.operator.mapper.OperatorMapper;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.dto.RobotRealtimeResponse;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RobotTelemetryServiceTest {

    private final RobotMapper robotMapper = mock(RobotMapper.class);
    private final OperatorMapper operatorMapper = mock(OperatorMapper.class);
    private final OperatorRobotAccessMapper accessMapper = mock(OperatorRobotAccessMapper.class);
    private final RobotConnectionMapper connectionMapper = mock(RobotConnectionMapper.class);
    private final RobotStateMapper stateMapper = mock(RobotStateMapper.class);
    private final RobotPositionMapper positionMapper = mock(RobotPositionMapper.class);
    private final RobotFactsheetMapper factsheetMapper = mock(RobotFactsheetMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final UUID robotId = UUID.randomUUID();

    private RobotTelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryService = new RobotTelemetryService(
            robotMapper,
            operatorMapper,
            accessMapper,
            connectionMapper,
            stateMapper,
            positionMapper,
            factsheetMapper,
            redisTemplate
        );
        authenticate("admin", operatorId, "admin");
        when(operatorMapper.selectById(operatorId)).thenReturn(operator(true));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRealtimeReturnsLatestConnectionStateAndPosition() {
        when(robotMapper.selectById(robotId)).thenReturn(robot());
        when(connectionMapper.selectOne(any())).thenReturn(latestConnection());
        when(stateMapper.selectOne(any())).thenReturn(latestState());
        when(positionMapper.selectOne(any())).thenReturn(latestPosition());
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(true);

        RobotRealtimeResponse response = telemetryService.getRealtime(robotId);

        assertThat(response.robot().online()).isTrue();
        assertThat(response.connection().online()).isTrue();
        assertThat(response.state().batterySoc()).isEqualTo((short) 100);
        assertThat(response.position().mapId()).isEqualTo("map-a");
    }

    @Test
    void getRealtimeMarksRobotOfflineWhenRedisMarkerIsMissing() {
        when(robotMapper.selectById(robotId)).thenReturn(robot());
        when(connectionMapper.selectOne(any())).thenReturn(latestConnection());
        when(stateMapper.selectOne(any())).thenReturn(latestState());
        when(positionMapper.selectOne(any())).thenReturn(latestPosition());
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(false);

        RobotRealtimeResponse response = telemetryService.getRealtime(robotId);

        assertThat(response.robot().online()).isFalse();
        assertThat(response.connection().online()).isFalse();
    }

    @Test
    void operatorWithoutRobotAccessCannotReadRealtime() {
        authenticate("operator01", UUID.randomUUID(), "operator");
        when(robotMapper.selectById(robotId)).thenReturn(robot());
        when(accessMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> telemetryService.getRealtime(robotId))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void getFactsheetReturnsLatestFactsheetPayload() {
        when(robotMapper.selectById(robotId)).thenReturn(robot());
        when(factsheetMapper.selectById(robotId)).thenReturn(factsheet());

        assertThat(telemetryService.getFactsheet(robotId).rawPayload()).containsKey("protocolFeatures");
    }

    @Test
    void getRealtimeHidesRawPayloadWithoutDebugPermission() {
        when(operatorMapper.selectById(operatorId)).thenReturn(operator(false));
        when(robotMapper.selectById(robotId)).thenReturn(robot());
        when(connectionMapper.selectOne(any())).thenReturn(latestConnection());
        when(stateMapper.selectOne(any())).thenReturn(latestState());
        when(positionMapper.selectOne(any())).thenReturn(latestPosition());

        RobotRealtimeResponse response = telemetryService.getRealtime(robotId);

        assertThat(response.state().rawPayload()).isNull();
    }

    private RobotConnectionEntity latestConnection() {
        RobotConnectionEntity connection = new RobotConnectionEntity();
        connection.setTime(OffsetDateTime.now());
        connection.setTenantId(tenantId);
        connection.setRobotId(robotId);
        connection.setConnectionState("connected");
        return connection;
    }

    private RobotStateEntity latestState() {
        RobotStateEntity state = new RobotStateEntity();
        state.setTime(OffsetDateTime.now());
        state.setTenantId(tenantId);
        state.setRobotId(robotId);
        state.setPosX(12.5);
        state.setPosY(23.5);
        state.setPosTheta(1.57);
        state.setMapId("map-a");
        state.setBatterySoc((short) 100);
        state.setOperatingMode("MANUAL");
        state.setOrderId("order-001");
        state.setRawPayload(Map.of("source", "mqtt"));
        return state;
    }

    private RobotPositionEntity latestPosition() {
        RobotPositionEntity position = new RobotPositionEntity();
        position.setTime(OffsetDateTime.now());
        position.setTenantId(tenantId);
        position.setRobotId(robotId);
        position.setPosX(12.5);
        position.setPosY(23.5);
        position.setPosTheta(1.57);
        position.setMapId("map-a");
        return position;
    }

    private RobotFactsheetEntity factsheet() {
        RobotFactsheetEntity factsheet = new RobotFactsheetEntity();
        factsheet.setRobotId(robotId);
        factsheet.setTenantId(tenantId);
        factsheet.setReceivedAt(OffsetDateTime.now());
        factsheet.setRawPayload(Map.of("protocolFeatures", Map.of("mobileRobotActions", List.of())));
        return factsheet;
    }

    private RobotEntity robot() {
        RobotEntity robot = new RobotEntity();
        robot.setId(robotId);
        robot.setTenantId(tenantId);
        robot.setExternalRobotId("AUTH-UNIT-1001");
        robot.setSerialNumber("UNIT-SN-01");
        robot.setManufacturer("JSYS");
        robot.setStatus("active");
        return robot;
    }

    private OperatorEntity operator(boolean debugPermission) {
        OperatorEntity operator = new OperatorEntity();
        operator.setId(operatorId);
        operator.setTenantId(tenantId);
        operator.setUsername("admin");
        operator.setRole("admin");
        operator.setDebugPermission(debugPermission);
        return operator;
    }

    private void authenticate(String username, UUID currentOperatorId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CurrentUser(currentOperatorId, tenantId, username, role, "Default Tenant", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            )
        );
    }
}
