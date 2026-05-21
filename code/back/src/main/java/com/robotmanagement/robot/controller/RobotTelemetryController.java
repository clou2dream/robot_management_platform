package com.robotmanagement.robot.controller;

import com.robotmanagement.robot.dto.RobotConnectionResponse;
import com.robotmanagement.robot.dto.RobotFactsheetResponse;
import com.robotmanagement.robot.dto.RobotPositionResponse;
import com.robotmanagement.robot.dto.RobotRealtimeResponse;
import com.robotmanagement.robot.dto.RobotStateResponse;
import com.robotmanagement.robot.service.RobotTelemetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    @GetMapping("/trajectory")
    public List<RobotPositionResponse> getTrajectory(
        @PathVariable UUID robotId,
        @RequestParam(defaultValue = "240") int limit
    ) {
        return telemetryService.getTrajectory(robotId, limit);
    }

    @GetMapping("/factsheet")
    public RobotFactsheetResponse getFactsheet(@PathVariable UUID robotId) {
        return telemetryService.getFactsheet(robotId);
    }

}
