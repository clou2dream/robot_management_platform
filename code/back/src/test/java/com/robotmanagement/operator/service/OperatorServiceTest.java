package com.robotmanagement.operator.service;

import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.operator.dto.CreateOperatorRequest;
import com.robotmanagement.operator.entity.OperatorEntity;
import com.robotmanagement.operator.mapper.OperatorMapper;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperatorServiceTest {

    private final OperatorMapper operatorMapper = mock(OperatorMapper.class);
    private final OperatorRobotAccessMapper accessMapper = mock(OperatorRobotAccessMapper.class);
    private final RobotMapper robotMapper = mock(RobotMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private OperatorService operatorService;

    @BeforeEach
    void setUp() {
        operatorService = new OperatorService(operatorMapper, accessMapper, robotMapper, passwordEncoder);
        authenticate("admin", adminId, "admin");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonAdminCannotManageOperators() {
        authenticate("operator01", UUID.randomUUID(), "operator");

        assertThatThrownBy(() -> operatorService.listOperators(1, 20, null))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void createOperatorEncodesPasswordAndInsertsTenantScopedUser() {
        when(operatorMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        ArgumentCaptor<OperatorEntity> operatorCaptor = ArgumentCaptor.forClass(OperatorEntity.class);

        operatorService.createOperator(new CreateOperatorRequest(" operator01 ", "secret123", "operator"));

        verify(operatorMapper).insert(operatorCaptor.capture());
        OperatorEntity operator = operatorCaptor.getValue();
        assertThat(operator.getTenantId()).isEqualTo(tenantId);
        assertThat(operator.getUsername()).isEqualTo("operator01");
        assertThat(operator.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(operator.getRole()).isEqualTo("operator");
        assertThat(operator.getPasswordResetRequired()).isFalse();
    }

    private void authenticate(String username, UUID operatorId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CurrentUser(operatorId, tenantId, username, role, "姒涙顓荤粔鐔稿煕", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            )
        );
    }
}
