package com.robotmanagement.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttMessageGuardServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID robotId = UUID.randomUUID();

    private MqttMessageGuardService guardService;

    @BeforeEach
    void setUp() {
        guardService = new MqttMessageGuardService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void rejectsDuplicateHeaderIdWithinSameChannel() throws Exception {
        String headerKey = "robot:" + robotId + ":mqtt:state:header:10";
        when(redisTemplate.hasKey(headerKey)).thenReturn(true);

        boolean accepted = guardService.accept(robotId, "state", message(10, "2026-05-19T02:00:00Z"));

        assertThat(accepted).isFalse();
        verify(valueOperations, never()).set(eq(headerKey), any(), any(Duration.class));
    }

    @Test
    void acceptsInvalidTimestampUsingServerTimeWithoutMovingLastTimestamp() throws Exception {
        String headerKey = "robot:" + robotId + ":mqtt:state:header:11";
        String lastTimestampKey = "robot:" + robotId + ":mqtt:state:lastTimestamp";
        when(redisTemplate.hasKey(headerKey)).thenReturn(false);

        boolean accepted = guardService.accept(robotId, "state", message(11, "not-a-time"));

        assertThat(accepted).isTrue();
        verify(valueOperations).set(eq(headerKey), any(), any(Duration.class));
        verify(valueOperations, never()).set(eq(lastTimestampKey), any(), any(Duration.class));
    }

    private JsonNode message(long headerId, String timestamp) throws Exception {
        return objectMapper.readTree("""
            {
              "headerId": %d,
              "timestamp": "%s",
              "version": "3.0.0",
              "manufacturer": "JSYS",
              "serialNumber": "RBT-SN-1001"
            }
            """.formatted(headerId, timestamp));
    }
}
