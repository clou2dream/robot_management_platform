package com.robotmanagement.mqtt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robotmanagement.openplatform.entity.OpenPlatformCredentialEntity;
import com.robotmanagement.openplatform.mapper.OpenPlatformCredentialMapper;
import com.robotmanagement.common.util.HashUtils;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttRobotAuthenticationServiceTest {

    private final RobotMapper robotMapper = mock(RobotMapper.class);
    private final OpenPlatformCredentialMapper credentialMapper = mock(OpenPlatformCredentialMapper.class);
    private final OperatorRobotAccessMapper accessMapper = mock(OperatorRobotAccessMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final MqttUnauthorizedMessageService unauthorizedMessageService = mock(MqttUnauthorizedMessageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID tenantId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();

    private MqttRobotAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new MqttRobotAuthenticationService(
            robotMapper,
            credentialMapper,
            accessMapper,
            redisTemplate,
            objectMapper,
            unauthorizedMessageService,
            Duration.ofMinutes(30)
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void authenticateOnlineCreatesRobotAndWritesSession() throws Exception {
        OpenPlatformCredentialEntity credential = credential();
        when(credentialMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(credential));
        when(robotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(accessMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        ArgumentCaptor<RobotEntity> robotCaptor = ArgumentCaptor.forClass(RobotEntity.class);
        ArgumentCaptor<OperatorRobotAccessEntity> accessCaptor = ArgumentCaptor.forClass(OperatorRobotAccessEntity.class);

        RobotEntity robot = authenticationService.authenticateOnline(
            "uagv/v3/JSYS/RBT-SN-1001/connection",
            new MqttTopicParts("JSYS", "RBT-SN-1001", "connection"),
            payload()
        );

        verify(robotMapper).insert(robotCaptor.capture());
        verify(accessMapper).insert(accessCaptor.capture());
        verify(valueOperations).set(
            eq("robot:auth-session:JSYS:RBT-SN-1001"),
            anyString(),
            any(Duration.class)
        );
        RobotEntity inserted = robotCaptor.getValue();
        OperatorRobotAccessEntity access = accessCaptor.getValue();
        assertThat(robot).isSameAs(inserted);
        assertThat(inserted.getTenantId()).isEqualTo(tenantId);
        assertThat(inserted.getExternalRobotId()).isEqualTo("AUTH-RBT-2001");
        assertThat(inserted.getSerialNumber()).isEqualTo("RBT-SN-1001");
        assertThat(inserted.getManufacturer()).isEqualTo("JSYS");
        assertThat(inserted.getSourceAppId()).isEqualTo("app-001");
        assertThat(access.getOperatorId()).isEqualTo(operatorId);
        assertThat(access.getRobotId()).isEqualTo(inserted.getId());
    }

    @Test
    void authenticateOnlineRejectsMissingIdentity() throws Exception {
        JsonNode root = objectMapper.readTree("""
            {
              "headerId": 1,
              "timestamp": "2026-05-19T02:00:00Z",
              "version": "3.0.0",
              "manufacturer": "JSYS",
              "serialNumber": "RBT-SN-1001",
              "connectionState": "ONLINE"
            }
            """);
        MqttTopicParts parts = new MqttTopicParts("JSYS", "RBT-SN-1001", "connection");

        RobotEntity robot = authenticationService.authenticateOnline("uagv/v3/JSYS/RBT-SN-1001/connection", parts, root);

        assertThat(robot).isNull();
        verify(unauthorizedMessageService).record(
            eq("uagv/v3/JSYS/RBT-SN-1001/connection"),
            eq(parts),
            eq(root),
            anyString()
        );
        verify(robotMapper, never()).insert(any(RobotEntity.class));
    }

    private JsonNode payload() throws Exception {
        return objectMapper.readTree("""
            {
              "headerId": 1,
              "timestamp": "2026-05-19T02:00:00Z",
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

    private OpenPlatformCredentialEntity credential() {
        OpenPlatformCredentialEntity credential = new OpenPlatformCredentialEntity();
        credential.setId(UUID.randomUUID());
        credential.setTenantId(tenantId);
        credential.setOperatorId(operatorId);
        credential.setAppId("app-001");
        credential.setApiKey("key-001");
        credential.setApiSecret(HashUtils.sha256Hex("secret-001"));
        return credential;
    }
}
