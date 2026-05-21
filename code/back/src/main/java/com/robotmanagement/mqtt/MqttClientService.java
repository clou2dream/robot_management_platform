package com.robotmanagement.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Component
public class MqttClientService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MqttClientService.class);

    private final MqttProperties properties;
    private final MqttInboundMessageHandler inboundMessageHandler;
    private final ObjectMapper objectMapper;

    private volatile MqttAsyncClient client;
    private volatile boolean running;

    public MqttClientService(
        MqttProperties properties,
        MqttInboundMessageHandler inboundMessageHandler,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.inboundMessageHandler = inboundMessageHandler;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWhenApplicationReady() {
        start();
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (!properties.isUsable()) {
            log.info("MQTT client is disabled. Set app.middleware.emqx.mqtt.enabled=true and host to consume robot telemetry.");
            return;
        }

        try {
            client = new MqttAsyncClient(properties.brokerUri(), properties.getClientId(), new MemoryPersistence());
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    subscribeInboundTopic(reconnect);
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    inboundMessageHandler.handle(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Publishing code waits on the delivery token directly.
                }
            });

            client.connect(connectOptions()).waitForCompletion(5000);
            running = true;
            log.info("Connected to MQTT broker {}", properties.brokerUri());
        } catch (MqttException ex) {
            running = false;
            log.warn("MQTT client did not start: {}", ex.getMessage());
        }
    }

    @Override
    public void stop() {
        MqttAsyncClient current = client;
        if (current != null) {
            try {
                if (current.isConnected()) {
                    current.disconnect().waitForCompletion(3000);
                }
                current.close();
            } catch (MqttException ex) {
                log.warn("MQTT client stop failed: {}", ex.getMessage());
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running && client != null && client.isConnected();
    }

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    public void publishJson(String topic, Object payload, int qos) {
        MqttAsyncClient current = client;
        if (current == null || !current.isConnected()) {
            throw new IllegalStateException("MQTT broker is not connected");
        }

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            MqttMessage message = new MqttMessage(bytes);
            message.setQos(qos);
            message.setRetained(false);
            current.publish(topic, message).waitForCompletion(5000);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("MQTT payload cannot be serialized", ex);
        } catch (MqttException ex) {
            throw new IllegalStateException("MQTT publish failed: " + ex.getMessage(), ex);
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        if (StringUtils.hasText(properties.getUsername())) {
            options.setUserName(properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            options.setPassword(properties.getPassword().toCharArray());
        }
        return options;
    }

    private void subscribeInboundTopic(boolean reconnect) {
        MqttAsyncClient current = client;
        if (current == null || !current.isConnected()) {
            return;
        }
        try {
            current.subscribe(properties.getInboundTopic(), properties.getQos()).waitForCompletion(5000);
            log.info("{} MQTT topic {}", reconnect ? "Resubscribed" : "Subscribed", properties.getInboundTopic());
        } catch (MqttException ex) {
            log.warn("MQTT subscribe failed: {}", ex.getMessage());
        }
    }
}
