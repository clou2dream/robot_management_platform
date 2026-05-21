package com.robotmanagement.robot.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robotmanagement.common.mybatis.PostgresJsonbTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName(value = "robot_states", autoResultMap = true)
public class RobotStateEntity {

    private OffsetDateTime time;

    private UUID tenantId;

    private UUID robotId;

    private Double posX;

    private Double posY;

    private Double posTheta;

    private String mapId;

    private Short batterySoc;

    private String operatingMode;

    private String orderId;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    public OffsetDateTime getTime() {
        return time;
    }

    public void setTime(OffsetDateTime time) {
        this.time = time;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getRobotId() {
        return robotId;
    }

    public void setRobotId(UUID robotId) {
        this.robotId = robotId;
    }

    public Double getPosX() {
        return posX;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public Double getPosTheta() {
        return posTheta;
    }

    public void setPosTheta(Double posTheta) {
        this.posTheta = posTheta;
    }

    public String getMapId() {
        return mapId;
    }

    public void setMapId(String mapId) {
        this.mapId = mapId;
    }

    public Short getBatterySoc() {
        return batterySoc;
    }

    public void setBatterySoc(Short batterySoc) {
        this.batterySoc = batterySoc;
    }

    public String getOperatingMode() {
        return operatingMode;
    }

    public void setOperatingMode(String operatingMode) {
        this.operatingMode = operatingMode;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }
}
