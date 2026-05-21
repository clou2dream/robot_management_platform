package com.robotmanagement.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robotmanagement.alert.service.AlertService;
import com.robotmanagement.realtime.RealtimeMessagePublisher;
import com.robotmanagement.robot.mapper.RobotConnectionMapper;
import com.robotmanagement.robot.mapper.RobotFactsheetMapper;
import com.robotmanagement.robot.mapper.RobotPositionMapper;
import com.robotmanagement.robot.mapper.RobotStateMapper;
import com.robotmanagement.task.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RobotTelemetryIngestionServiceTest {

    private final RobotConnectionMapper connectionMapper = mock(RobotConnectionMapper.class);
    private final RobotStateMapper stateMapper = mock(RobotStateMapper.class);
    private final RobotPositionMapper positionMapper = mock(RobotPositionMapper.class);
    private final RobotFactsheetMapper factsheetMapper = mock(RobotFactsheetMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RealtimeMessagePublisher realtimeMessagePublisher = mock(RealtimeMessagePublisher.class);
    private final AlertService alertService = mock(AlertService.class);
    private final OrderMapper orderMapper = mock(OrderMapper.class);

    @Test
    void markOnlineUsesConfiguredTtl() {
        UUID robotId = UUID.randomUUID();
        RobotTelemetryIngestionService service = newService(Duration.ofSeconds(60));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.markOnline(robotId);

        verify(valueOperations).set("robot:" + robotId + ":online", "1", Duration.ofSeconds(60));
    }

    private RobotTelemetryIngestionService newService(Duration onlineTtl) {
        return new RobotTelemetryIngestionService(
            connectionMapper,
            stateMapper,
            positionMapper,
            factsheetMapper,
            redisTemplate,
            new ObjectMapper(),
            realtimeMessagePublisher,
            alertService,
            orderMapper,
            onlineTtl
        );
    }
}
