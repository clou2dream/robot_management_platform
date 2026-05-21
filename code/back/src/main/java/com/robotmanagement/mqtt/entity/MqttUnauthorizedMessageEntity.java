package com.robotmanagement.mqtt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robotmanagement.common.mybatis.PostgresJsonbTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName(value = "mqtt_unauthorized_messages", autoResultMap = true)
public class MqttUnauthorizedMessageEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private OffsetDateTime receivedAt;

    private String topic;

    private String manufacturer;

    private String serialNumber;

    private String channel;

    private String appId;

    private String robotUniqueId;

    private String reason;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getRobotUniqueId() {
        return robotUniqueId;
    }

    public void setRobotUniqueId(String robotUniqueId) {
        this.robotUniqueId = robotUniqueId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }
}
