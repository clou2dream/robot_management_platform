package com.robotmanagement.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robotmanagement.mqtt.entity.MqttUnauthorizedMessageEntity;
import com.robotmanagement.mqtt.mapper.MqttUnauthorizedMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MqttUnauthorizedMessageServiceTest {

    private final MqttUnauthorizedMessageMapper mapper = mock(MqttUnauthorizedMessageMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MqttUnauthorizedMessageService service = newService(true, 8192, 10);

    @Test
    void recordRedactsApiSecretInRawPayload() throws Exception {
        JsonNode root = objectMapper.readTree("""
            {
              "headerId": 1,
              "timestamp": "2026-05-20T02:00:00Z",
              "version": "3.0.0",
              "manufacturer": "JSYS",
              "serialNumber": "RBT-SN-1001",
              "connectionState": "ONLINE",
              "identity": {
                "appid": "app-001",
                "apikey": "key-001",
                "apisecret": "secret-001",
                "robotUniqueId": "AUTH-RBT-2001"
              }
            }
            """);
        ArgumentCaptor<MqttUnauthorizedMessageEntity> captor =
            ArgumentCaptor.forClass(MqttUnauthorizedMessageEntity.class);

        service.record(
            "uagv/v3/JSYS/RBT-SN-1001/connection",
            new MqttTopicParts("JSYS", "RBT-SN-1001", "connection"),
            root,
            "identity 三元组未匹配到平台凭证"
        );

        verify(mapper).insert(captor.capture());
        MqttUnauthorizedMessageEntity message = captor.getValue();
        Map<String, Object> identity = (Map<String, Object>) message.getRawPayload().get("identity");
        assertThat(message.getAppId()).isEqualTo("app-001");
        assertThat(message.getRobotUniqueId()).isEqualTo("AUTH-RBT-2001");
        assertThat(identity).containsEntry("apisecret", "***REDACTED***");
        assertThat(identity).containsEntry("apikey", "key-001");
    }

    @Test
    void recordSkipsInsertWhenDisabled() throws Exception {
        MqttUnauthorizedMessageService disabledService = newService(false, 8192, 10);

        disabledService.record(
            "uagv/v3/JSYS/RBT-SN-1001/connection",
            new MqttTopicParts("JSYS", "RBT-SN-1001", "connection"),
            validPayload(),
            "disabled"
        );

        verify(mapper, never()).insert(any(MqttUnauthorizedMessageEntity.class));
    }

    @Test
    void recordStoresTruncatedPayloadSummaryWhenPayloadIsTooLarge() throws Exception {
        MqttUnauthorizedMessageService truncatingService = newService(true, 120, 10);
        JsonNode root = objectMapper.readTree("""
            {
              "headerId": 1,
              "timestamp": "2026-05-20T02:00:00Z",
              "version": "3.0.0",
              "manufacturer": "JSYS",
              "serialNumber": "RBT-SN-1001",
              "identity": {
                "appid": "app-001",
                "apikey": "key-001",
                "apisecret": "secret-001",
                "robotUniqueId": "AUTH-RBT-2001"
              },
              "noise": "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
            }
            """);
        ArgumentCaptor<MqttUnauthorizedMessageEntity> captor =
            ArgumentCaptor.forClass(MqttUnauthorizedMessageEntity.class);

        truncatingService.record(
            "uagv/v3/JSYS/RBT-SN-1001/connection",
            new MqttTopicParts("JSYS", "RBT-SN-1001", "connection"),
            root,
            "payload too large"
        );

        verify(mapper).insert(captor.capture());
        Map<String, Object> rawPayload = captor.getValue().getRawPayload();
        assertThat(rawPayload).containsEntry("_truncated", true);
        assertThat(rawPayload.get("_payloadPreview").toString()).doesNotContain("secret-001");
    }

    @Test
    void recordRateLimitsRepeatedSameReason() throws Exception {
        MqttUnauthorizedMessageService rateLimitedService = newService(true, 8192, 2);
        JsonNode root = validPayload();

        for (int i = 0; i < 5; i++) {
            rateLimitedService.record(
                "uagv/v3/JSYS/RBT-SN-1001/connection",
                new MqttTopicParts("JSYS", "RBT-SN-1001", "connection"),
                root,
                "same reason"
            );
        }

        verify(mapper, org.mockito.Mockito.times(2)).insert(any(MqttUnauthorizedMessageEntity.class));
    }

    @Test
    void cleanupExpiredRecordsDeletesByRetention() {
        service.cleanupExpiredRecords();

        verify(mapper).delete(any());
    }

    private MqttUnauthorizedMessageService newService(boolean enabled, int maxPayloadBytes, int maxPerMinutePerKey) {
        return new MqttUnauthorizedMessageService(
            mapper,
            objectMapper,
            new MqttUnauthorizedMessageRecordProperties(
                enabled,
                maxPayloadBytes,
                maxPerMinutePerKey,
                Duration.ofDays(7)
            )
        );
    }

    private JsonNode validPayload() throws Exception {
        return objectMapper.readTree("""
            {
              "headerId": 1,
              "timestamp": "2026-05-20T02:00:00Z",
              "version": "3.0.0",
              "manufacturer": "JSYS",
              "serialNumber": "RBT-SN-1001",
              "connectionState": "ONLINE",
              "identity": {
                "appid": "app-001",
                "apikey": "key-001",
                "apisecret": "secret-001",
                "robotUniqueId": "AUTH-RBT-2001"
              }
            }
            """);
    }
}
