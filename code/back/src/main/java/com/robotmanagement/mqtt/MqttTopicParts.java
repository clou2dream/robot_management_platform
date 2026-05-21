package com.robotmanagement.mqtt;

public record MqttTopicParts(
    String manufacturer,
    String serialNumber,
    String channel
) {
}
