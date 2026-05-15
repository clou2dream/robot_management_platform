package com.robotmanagement.operator.controller;

import com.robotmanagement.common.api.PageResult;
import com.robotmanagement.operator.dto.CreateOperatorRequest;
import com.robotmanagement.operator.dto.GrantRobotAccessRequest;
import com.robotmanagement.operator.dto.OperatorResponse;
import com.robotmanagement.operator.dto.UpdateOperatorRoleRequest;
import com.robotmanagement.operator.service.OperatorService;
import com.robotmanagement.robot.dto.RobotResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/operators")
public class OperatorController {

    private final OperatorService operatorService;

    public OperatorController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    @GetMapping
    public PageResult<OperatorResponse> listOperators(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword
    ) {
        return operatorService.listOperators(page, size, keyword);
    }

    @PostMapping
    public OperatorResponse createOperator(@Valid @RequestBody CreateOperatorRequest request) {
        return operatorService.createOperator(request);
    }

    @PutMapping("/{operatorId}/role")
    public OperatorResponse updateRole(
        @PathVariable UUID operatorId,
        @Valid @RequestBody UpdateOperatorRoleRequest request
    ) {
        return operatorService.updateRole(operatorId, request);
    }

    @DeleteMapping("/{operatorId}")
    public void deleteOperator(@PathVariable UUID operatorId) {
        operatorService.deleteOperator(operatorId);
    }

    @GetMapping("/{operatorId}/robots")
    public List<RobotResponse> listRobotAccess(@PathVariable UUID operatorId) {
        return operatorService.listRobotAccess(operatorId);
    }

    @PostMapping("/{operatorId}/robots")
    public void grantRobotAccess(
        @PathVariable UUID operatorId,
        @Valid @RequestBody GrantRobotAccessRequest request
    ) {
        operatorService.grantRobotAccess(operatorId, request);
    }

    @DeleteMapping("/{operatorId}/robots/{robotId}")
    public void revokeRobotAccess(@PathVariable UUID operatorId, @PathVariable UUID robotId) {
        operatorService.revokeRobotAccess(operatorId, robotId);
    }
}
