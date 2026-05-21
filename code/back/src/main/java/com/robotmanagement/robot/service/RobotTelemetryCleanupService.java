package com.robotmanagement.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.robot.entity.RobotPositionEntity;
import com.robotmanagement.robot.entity.RobotStateEntity;
import com.robotmanagement.robot.mapper.RobotPositionMapper;
import com.robotmanagement.robot.mapper.RobotStateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class RobotTelemetryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RobotTelemetryCleanupService.class);

    private final RobotStateMapper stateMapper;
    private final RobotPositionMapper positionMapper;
    private final Duration stateRetention;
    private final Duration visualizationRetention;

    public RobotTelemetryCleanupService(
        RobotStateMapper stateMapper,
        RobotPositionMapper positionMapper,
        @Value("${app.robot.state-retention:P7D}") Duration stateRetention,
        @Value("${app.robot.visualization-retention:PT6H}") Duration visualizationRetention
    ) {
        this.stateMapper = stateMapper;
        this.positionMapper = positionMapper;
        this.stateRetention = sanitizeRetention(stateRetention);
        this.visualizationRetention = sanitizeRetention(visualizationRetention);
    }

    /**
     * Periodically removes historical telemetry beyond the configured retention windows.
     */
    @Scheduled(
        fixedDelayString = "${app.robot.telemetry-cleanup-delay-ms:3600000}",
        initialDelayString = "${app.robot.telemetry-cleanup-initial-delay-ms:300000}"
    )
    public void cleanupExpiredTelemetry() {
        cleanupStates();
        cleanupPositions();
    }

    int cleanupStates() {
        if (stateRetention == null) {
            return 0;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minus(stateRetention);
        int deleted = stateMapper.delete(
            new LambdaQueryWrapper<RobotStateEntity>()
                .lt(RobotStateEntity::getTime, cutoff)
        );
        if (deleted > 0) {
            log.info("Cleaned {} expired robot state records before {}", deleted, cutoff);
        }
        return deleted;
    }

    int cleanupPositions() {
        if (visualizationRetention == null) {
            return 0;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minus(visualizationRetention);
        int deleted = positionMapper.delete(
            new LambdaQueryWrapper<RobotPositionEntity>()
                .lt(RobotPositionEntity::getTime, cutoff)
        );
        if (deleted > 0) {
            log.info("Cleaned {} expired robot visualization records before {}", deleted, cutoff);
        }
        return deleted;
    }

    /**
     * Null disables cleanup for that telemetry type; zero or negative values are ignored as invalid.
     */
    private Duration sanitizeRetention(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return null;
        }
        return value;
    }
}
