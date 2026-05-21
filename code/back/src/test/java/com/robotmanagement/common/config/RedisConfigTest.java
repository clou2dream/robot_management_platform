package com.robotmanagement.common.config;

import com.robotmanagement.common.security.RefreshSession;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void redisTemplateSerializesRefreshSessionWithOffsetDateTime() {
        RedisConfig redisConfig = new RedisConfig();
        JacksonConfig jacksonConfig = new JacksonConfig();
        RedisTemplate<String, Object> redisTemplate = redisConfig.redisTemplate(
            mock(RedisConnectionFactory.class),
            jacksonConfig.objectMapper()
        );
        RedisSerializer<?> serializer = redisTemplate.getValueSerializer();

        RefreshSession session = new RefreshSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            OffsetDateTime.now()
        );

        byte[] serialized = serialize(serializer, session);
        Object deserialized = serializer.deserialize(serialized);

        assertThat(deserialized).isInstanceOf(RefreshSession.class);
        RefreshSession restored = (RefreshSession) deserialized;
        assertThat(restored.tokenId()).isEqualTo(session.tokenId());
        assertThat(restored.operatorId()).isEqualTo(session.operatorId());
        assertThat(restored.tenantId()).isEqualTo(session.tenantId());
        assertThat(restored.issuedAt().toInstant()).isEqualTo(session.issuedAt().toInstant());
    }

    @SuppressWarnings("unchecked")
    private static byte[] serialize(RedisSerializer<?> serializer, Object value) {
        return ((RedisSerializer<Object>) serializer).serialize(value);
    }
}
