package com.robotmanagement.mqtt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    MqttProperties.class,
    MqttUnauthorizedMessageRecordProperties.class
})
public class MqttConfig {
}
