package com.robotmanagement.robot.controller;

import com.robotmanagement.robot.dto.CreateDemoTelemetryRequest;
import com.robotmanagement.robot.dto.RobotConnectionResponse;
import com.robotmanagement.robot.dto.RobotPositionResponse;
import com.robotmanagement.robot.dto.RobotRealtimeResponse;
import com.robotmanagement.robot.dto.RobotStateResponse;
import com.robotmanagement.robot.service.RobotTelemetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/robots/{robotId}")
public class RobotTelemetryController {

    private final RobotTelemetryService telemetryService;

    public RobotTelemetryController(RobotTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @GetMapping("/realtime")
    public RobotRealtimeResponse getRealtime(@PathVariable UUID robotId) {
        return telemetryService.getRealtime(robotId);
    }

    @GetMapping("/connection")
    public RobotConnectionResponse getConnection(@PathVariable UUID robotId) {
        return telemetryService.getConnection(robotId);
    }

    @GetMapping("/state")
    public RobotStateResponse getState(@PathVariable UUID robotId) {
        return telemetryService.getState(robotId);
    }

    @GetMapping("/position")
    public RobotPositionResponse getPosition(@PathVariable UUID robotId) {
        return telemetryService.getPosition(robotId);
    }

    @PostMapping("/telemetry/demo")
    public RobotRealtimeResponse createDemoTelemetry(
        @PathVariable UUID robotId,
        @RequestBody(required = false) CreateDemoTelemetryRequest request
    ) {
        return telemetryService.createDemoTelemetry(robotId, request);
    }
}
