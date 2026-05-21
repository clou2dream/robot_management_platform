package com.robotmanagement.robot.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robotmanagement.common.mybatis.PostgresJsonbTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName(value = "robot_factsheets", autoResultMap = true)
public class RobotFactsheetEntity {

    @TableId(type = IdType.INPUT)
    private UUID robotId;

    private UUID tenantId;

    private OffsetDateTime receivedAt;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    public UUID getRobotId() {
        return robotId;
    }

    public void setRobotId(UUID robotId) {
        this.robotId = robotId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }
}
