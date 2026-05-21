package com.robotmanagement.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.mapper.RobotMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RobotOfflineMonitorService {

    private static final Duration OFFLINE_GRACE = Duration.ofSeconds(15);

    private final RobotMapper robotMapper;
    private final RobotTelemetryIngestionService telemetryIngestionService;
    private final long scanPageSize;

    public RobotOfflineMonitorService(
        RobotMapper robotMapper,
        RobotTelemetryIngestionService telemetryIngestionService,
        @Value("${app.robot.offline-scan-page-size:500}") long scanPageSize
    ) {
        this.robotMapper = robotMapper;
        this.telemetryIngestionService = telemetryIngestionService;
        this.scanPageSize = scanPageSize;
    }

    /**
     * Scans active robots in pages and converts expired online TTLs into CONNECTION_BROKEN events.
     * The Redis online key is the fast signal; the last connection state and lastSeen timestamp prevent
     * robots that already reported OFFLINE from being repeatedly marked as broken.
     */
    @Transactional
    @Scheduled(
        fixedDelayString = "${app.robot.offline-scan-delay-ms:5000}",
        initialDelayString = "${app.robot.offline-scan-initial-delay-ms:10000}"
    )
    public void detectExpiredOnlineTtl() {
        OffsetDateTime now = OffsetDateTime.now();
        long current = 1;
        long size = Math.max(1, scanPageSize);
        Page<RobotEntity> page;
        do {
            page = robotMapper.selectPage(
                new Page<>(current, size, false),
                new LambdaQueryWrapper<RobotEntity>()
                    .eq(RobotEntity::getStatus, "active")
                    .orderByAsc(RobotEntity::getId)
            );
            for (RobotEntity robot : page.getRecords()) {
                if (telemetryIngestionService.isMarkedOnline(robot.getId())) {
                    continue;
                }
                if (!"ONLINE".equalsIgnoreCase(telemetryIngestionService.lastConnectionState(robot.getId()))) {
                    continue;
                }
                OffsetDateTime lastSeenAt = telemetryIngestionService.lastSeenAt(robot.getId());
                if (lastSeenAt == null || lastSeenAt.plus(OFFLINE_GRACE).isAfter(now)) {
                    continue;
                }
                telemetryIngestionService.ingestConnectionBrokenByTimeout(robot, now, lastSeenAt);
            }
            current++;
        } while (!page.getRecords().isEmpty() && page.getRecords().size() == size);
    }
}
