package com.robotmanagement.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.robot.entity.RobotPositionEntity;
import com.robotmanagement.robot.entity.RobotStateEntity;
import com.robotmanagement.robot.mapper.RobotPositionMapper;
import com.robotmanagement.robot.mapper.RobotStateMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RobotTelemetryCleanupServiceTest {

    private final RobotStateMapper stateMapper = mock(RobotStateMapper.class);
    private final RobotPositionMapper positionMapper = mock(RobotPositionMapper.class);

    @Test
    void cleanupExpiredTelemetryDeletesStateAndVisualizationByRetention() {
        RobotTelemetryCleanupService service = new RobotTelemetryCleanupService(
            stateMapper,
            positionMapper,
            Duration.ofDays(7),
            Duration.ofHours(6)
        );
        when(stateMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);
        when(positionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(5);

        service.cleanupExpiredTelemetry();

        verify(stateMapper).delete(any(LambdaQueryWrapper.class));
        verify(positionMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void cleanupSkipsTelemetryWhenRetentionIsInvalid() {
        RobotTelemetryCleanupService service = new RobotTelemetryCleanupService(
            stateMapper,
            positionMapper,
            Duration.ZERO,
            Duration.ofSeconds(-1)
        );

        assertThat(service.cleanupStates()).isZero();
        assertThat(service.cleanupPositions()).isZero();
        verify(stateMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(positionMapper, never()).delete(any(LambdaQueryWrapper.class));
    }
}
