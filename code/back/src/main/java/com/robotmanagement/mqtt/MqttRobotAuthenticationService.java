package com.robotmanagement.mqtt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robotmanagement.openplatform.entity.OpenPlatformCredentialEntity;
import com.robotmanagement.openplatform.mapper.OpenPlatformCredentialMapper;
import com.robotmanagement.operator.entity.OperatorRobotAccessEntity;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import com.robotmanagement.common.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MqttRobotAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(MqttRobotAuthenticationService.class);

    private final RobotMapper robotMapper;
    private final OpenPlatformCredentialMapper credentialMapper;
    private final OperatorRobotAccessMapper accessMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MqttUnauthorizedMessageService unauthorizedMessageService;
    private final Duration authSessionTtl;

    public MqttRobotAuthenticationService(
        RobotMapper robotMapper,
        OpenPlatformCredentialMapper credentialMapper,
        OperatorRobotAccessMapper accessMapper,
        StringRedisTemplate stringRedisTemplate,
        ObjectMapper objectMapper,
        MqttUnauthorizedMessageService unauthorizedMessageService,
        @Value("${app.robot.mqtt-auth-session-ttl:PT30M}") Duration authSessionTtl
    ) {
        this.robotMapper = robotMapper;
        this.credentialMapper = credentialMapper;
        this.accessMapper = accessMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.unauthorizedMessageService = unauthorizedMessageService;
        this.authSessionTtl = sanitizeAuthSessionTtl(authSessionTtl);
    }

    /**
     * Authenticates a connection ONLINE payload with appid/apikey/apisecret and creates the robot if needed.
     */
    @Transactional
    public RobotEntity authenticateOnline(String topic, MqttTopicParts parts, JsonNode root) {
        JsonNode identity = root.path("identity");
        String appId = trim(text(identity, "appid"));
        String apiKey = trim(text(identity, "apikey"));
        String apiSecret = trim(text(identity, "apisecret"));
        String robotUniqueId = trim(text(identity, "robotUniqueId"));
        if (!StringUtils.hasText(appId)
            || !StringUtils.hasText(apiKey)
            || !StringUtils.hasText(apiSecret)
            || !StringUtils.hasText(robotUniqueId)) {
            unauthorizedMessageService.record(topic, parts, root, "ONLINE 缺少 identity 三元组或 robotUniqueId");
            return null;
        }

        List<OpenPlatformCredentialEntity> credentials = credentialMapper.selectList(
            new LambdaQueryWrapper<OpenPlatformCredentialEntity>()
                .eq(OpenPlatformCredentialEntity::getAppId, appId)
                .eq(OpenPlatformCredentialEntity::getApiKey, apiKey)
                .eq(OpenPlatformCredentialEntity::getApiSecret, HashUtils.sha256Hex(apiSecret))
                .last("limit 2")
        );
        if (credentials.isEmpty()) {
            unauthorizedMessageService.record(topic, parts, root, "identity 三元组未匹配到平台凭证");
            return null;
        }
        if (credentials.size() > 1) {
            unauthorizedMessageService.record(topic, parts, root, "identity 三元组匹配到多组平台凭证");
            return null;
        }

        OpenPlatformCredentialEntity credential = credentials.get(0);
        RobotEntity robot = upsertRobot(parts, credential, robotUniqueId);
        grantOperatorAccess(credential.getOperatorId(), robot.getId());
        writeSession(parts, credential, robot, robotUniqueId);
        return robot;
    }

    /**
     * Resolves a previously authenticated robot for state, visualization, factsheet, and non-ONLINE connection messages.
     */
    public RobotEntity authenticatedRobot(String topic, MqttTopicParts parts, JsonNode root) {
        MqttRobotSession session = readSession(parts);
        if (session == null) {
            unauthorizedMessageService.record(topic, parts, root, "设备未完成 connection ONLINE 鉴权");
            return null;
        }
        RobotEntity robot = robotMapper.selectById(session.robotId());
        if (robot == null || !"active".equals(robot.getStatus())) {
            clearSession(parts);
            unauthorizedMessageService.record(topic, parts, root, "已认证设备对应机器人不存在或非 active");
            return null;
        }
        return robot;
    }

    /**
     * Extends the short-lived MQTT auth session after a valid authenticated telemetry message.
     */
    public void touchSession(MqttTopicParts parts) {
        String key = sessionKey(parts);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(value)) {
            stringRedisTemplate.opsForValue().set(key, value, authSessionTtl);
        }
    }

    /**
     * Clears the auth session when the robot reports a terminal non-ONLINE connection state.
     */
    public void clearSession(MqttTopicParts parts) {
        stringRedisTemplate.delete(sessionKey(parts));
    }

    /**
     * Finds an existing robot by stable identity or creates a new active robot for the matched credential tenant.
     */
    private RobotEntity upsertRobot(
        MqttTopicParts parts,
        OpenPlatformCredentialEntity credential,
        String robotUniqueId
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        RobotEntity robot = robotMapper.selectOne(
            new LambdaQueryWrapper<RobotEntity>()
                .eq(RobotEntity::getTenantId, credential.getTenantId())
                .eq(RobotEntity::getExternalRobotId, robotUniqueId)
                .last("limit 1")
        );
        if (robot == null) {
            robot = robotMapper.selectOne(
                new LambdaQueryWrapper<RobotEntity>()
                    .eq(RobotEntity::getTenantId, credential.getTenantId())
                    .eq(RobotEntity::getManufacturer, parts.manufacturer())
                    .eq(RobotEntity::getSerialNumber, parts.serialNumber())
                    .last("limit 1")
            );
        }
        if (robot == null) {
            robot = robotMapper.selectOne(
                new LambdaQueryWrapper<RobotEntity>()
                    .eq(RobotEntity::getTenantId, credential.getTenantId())
                    .eq(RobotEntity::getSerialNumber, parts.serialNumber())
                    .last("limit 1")
            );
        }

        if (robot == null) {
            robot = new RobotEntity();
            robot.setId(UUID.randomUUID());
            robot.setTenantId(credential.getTenantId());
            robot.setCreatedAt(now);
            robotMapper.insert(populateRobot(robot, parts, credential, robotUniqueId, now));
            return robot;
        }

        populateRobot(robot, parts, credential, robotUniqueId, now);
        robotMapper.updateById(robot);
        return robot;
    }

    /**
     * Copies identity fields from the MQTT topic and platform credential onto the robot record.
     */
    private RobotEntity populateRobot(
        RobotEntity robot,
        MqttTopicParts parts,
        OpenPlatformCredentialEntity credential,
        String robotUniqueId,
        OffsetDateTime now
    ) {
        robot.setExternalRobotId(robotUniqueId);
        robot.setSerialNumber(parts.serialNumber());
        robot.setManufacturer(parts.manufacturer());
        robot.setSourceAppId(credential.getAppId());
        robot.setStatus("active");
        robot.setSyncedAt(now);
        robot.setUpdatedAt(now);
        return robot;
    }

    /**
     * Gives the credential owner access to the robot when the robot is first discovered or re-bound.
     */
    private void grantOperatorAccess(UUID operatorId, UUID robotId) {
        Long count = accessMapper.selectCount(
            new LambdaQueryWrapper<OperatorRobotAccessEntity>()
                .eq(OperatorRobotAccessEntity::getOperatorId, operatorId)
                .eq(OperatorRobotAccessEntity::getRobotId, robotId)
        );
        if (count != null && count > 0) {
            return;
        }
        OperatorRobotAccessEntity access = new OperatorRobotAccessEntity();
        access.setOperatorId(operatorId);
        access.setRobotId(robotId);
        access.setGrantedAt(OffsetDateTime.now());
        accessMapper.insert(access);
    }

    /**
     * Stores the authenticated robot identity in Redis so later MQTT messages do not need to carry secrets.
     */
    private void writeSession(
        MqttTopicParts parts,
        OpenPlatformCredentialEntity credential,
        RobotEntity robot,
        String robotUniqueId
    ) {
        try {
            MqttRobotSession session = new MqttRobotSession(
                robot.getId(),
                robot.getTenantId(),
                credential.getOperatorId(),
                credential.getAppId(),
                robotUniqueId,
                parts.manufacturer(),
                parts.serialNumber(),
                OffsetDateTime.now().toString()
            );
            stringRedisTemplate.opsForValue().set(sessionKey(parts), objectMapper.writeValueAsString(session), authSessionTtl);
        } catch (Exception ex) {
            log.warn("Failed to write MQTT auth session for {}/{}: {}", parts.manufacturer(), parts.serialNumber(), ex.getMessage());
        }
    }

    /**
     * Keeps a broken or missing duration from expiring the auth session immediately.
     */
    private Duration sanitizeAuthSessionTtl(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return Duration.ofMinutes(30);
        }
        return value;
    }

    /**
     * Reads and validates the Redis auth session for a robot topic.
     */
    private MqttRobotSession readSession(MqttTopicParts parts) {
        String value = stringRedisTemplate.opsForValue().get(sessionKey(parts));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, MqttRobotSession.class);
        } catch (Exception ex) {
            log.warn("Failed to read MQTT auth session for {}/{}: {}", parts.manufacturer(), parts.serialNumber(), ex.getMessage());
            clearSession(parts);
            return null;
        }
    }

    /**
     * Uses manufacturer and serialNumber because they are stable in every topic after ONLINE.
     */
    private String sessionKey(MqttTopicParts parts) {
        return "robot:auth-session:" + parts.manufacturer() + ":" + parts.serialNumber();
    }

    /**
     * Reads a text field only when it is actually represented as JSON text.
     */
    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() ? node.asText() : null;
    }

    /**
     * Normalizes blank credential fields to null for easier validation.
     */
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
