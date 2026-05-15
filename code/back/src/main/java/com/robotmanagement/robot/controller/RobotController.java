package com.robotmanagement.robot.controller;

import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.robot.dto.RobotResponse;
import com.robotmanagement.robot.dto.RobotSyncRequest;
import com.robotmanagement.robot.dto.RobotSyncResult;
import com.robotmanagement.robot.dto.RobotSyncStatusResponse;
import com.robotmanagement.robot.service.RobotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/robots")
public class RobotController {

    private final RobotService robotService;

    public RobotController(RobotService robotService) {
        this.robotService = robotService;
    }

    @GetMapping
    public PageResult<RobotResponse> listRobots(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        return robotService.listRobots(page, size, keyword, status);
    }

    @GetMapping("/{robotId}")
    public RobotResponse getRobot(@PathVariable UUID robotId) {
        return robotService.getRobot(robotId);
    }

    @GetMapping("/{robotId}/sync-status")
    public RobotSyncStatusResponse getSyncStatus(@PathVariable UUID robotId) {
        return robotService.getSyncStatus(robotId);
    }

    @PostMapping("/sync")
    public RobotSyncResult syncRobots(@Valid @RequestBody(required = false) RobotSyncRequest request) {
        return robotService.syncRobots(request);
    }
}
