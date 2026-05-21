package com.robotmanagement.alert.service;

import com.robotmanagement.alert.dto.AlertResponse;
import com.robotmanagement.alert.entity.AlertEntity;
import com.robotmanagement.alert.mapper.AlertMapper;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.realtime.RealtimeMessagePublisher;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private final AlertMapper alertMapper = mock(AlertMapper.class);
    private final RobotMapper robotMapper = mock(RobotMapper.class);
    private final OperatorRobotAccessMapper accessMapper = mock(OperatorRobotAccessMapper.class);
    private final RealtimeMessagePublisher realtimeMessagePublisher = mock(RealtimeMessagePublisher.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final UUID robotId = UUID.randomUUID();
    private final UUID alertId = UUID.randomUUID();

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(alertMapper, robotMapper, accessMapper, realtimeMessagePublisher);
        authenticate("admin", operatorId, "admin");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveAlertMarksOpenAlertResolvedAndPublishesRealtimeMessage() {
        AlertEntity alert = openAlert();
        when(alertMapper.selectById(alertId)).thenReturn(alert);
        when(robotMapper.selectById(robotId)).thenReturn(robot());
        ArgumentCaptor<AlertResponse> responseCaptor = ArgumentCaptor.forClass(AlertResponse.class);

        AlertResponse response = alertService.resolveAlert(alertId);

        verify(alertMapper).updateById(alert);
        verify(realtimeMessagePublisher).alert(responseCaptor.capture());
        assertThat(alert.getResolvedAt()).isNotNull();
        assertThat(response.status()).isEqualTo("resolved");
        assertThat(responseCaptor.getValue().status()).isEqualTo("resolved");
        assertThat(responseCaptor.getValue().robotSn()).isEqualTo("UNIT-SN-01");
    }

    @Test
    void viewerCannotResolveAlert() {
        authenticate("viewer01", UUID.randomUUID(), "viewer");

        assertThatThrownBy(() -> alertService.resolveAlert(alertId))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void raiseSystemAlertReusesExistingOpenAlert() {
        AlertEntity existing = openAlert();
        when(alertMapper.selectOne(any())).thenReturn(existing);
        when(robotMapper.selectById(robotId)).thenReturn(robot());

        AlertResponse response = alertService.raiseSystemAlert(
            tenantId,
            robotId,
            "fatal",
            "EMERGENCY_STOP",
            "Emergency stop pressed",
            "Check the site",
            Map.of("source", "mqtt")
        );

        verify(alertMapper, never()).insert(any(AlertEntity.class));
        verify(realtimeMessagePublisher, never()).alert(any());
        assertThat(response.id()).isEqualTo(alertId);
        assertThat(response.status()).isEqualTo("open");
    }

    private AlertEntity openAlert() {
        AlertEntity alert = new AlertEntity();
        alert.setId(alertId);
        alert.setTenantId(tenantId);
        alert.setRobotId(robotId);
        alert.setTriggeredAt(OffsetDateTime.now().minusMinutes(5));
        alert.setLevel("WARNING");
        alert.setErrorType("LOW_BATTERY");
        alert.setDescription("Battery low");
        alert.setHint("Charge robot");
        alert.setRawError(Map.of("batterySoc", 12));
        return alert;
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
