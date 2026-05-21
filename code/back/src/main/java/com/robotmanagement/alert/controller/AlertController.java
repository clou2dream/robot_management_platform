package com.robotmanagement.alert.controller;

import com.robotmanagement.alert.dto.AlertResponse;
import com.robotmanagement.alert.dto.AlertSummaryResponse;
import com.robotmanagement.alert.service.AlertService;
import com.robotmanagement.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public PageResult<AlertResponse> listAlerts(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String level,
        @RequestParam(required = false) UUID robotId,
        @RequestParam(required = false) String keyword
    ) {
        return alertService.listAlerts(page, size, status, level, robotId, keyword);
    }

    @GetMapping("/summary")
    public AlertSummaryResponse getSummary() {
        return alertService.getSummary();
    }

    @GetMapping("/{alertId}")
    public AlertResponse getAlert(@PathVariable UUID alertId) {
        return alertService.getAlert(alertId);
    }

    @PutMapping("/{alertId}/resolve")
    public AlertResponse resolveAlert(@PathVariable UUID alertId) {
        return alertService.resolveAlert(alertId);
    }

}
