package com.robotmanagement.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RealtimeMessagePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeMessagePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void robotConnection(UUID robotId, Object payload) {
        messagingTemplate.convertAndSend("/topic/robots/" + robotId + "/connection", payload);
    }

    public void robotState(UUID robotId, Object payload) {
        messagingTemplate.convertAndSend("/topic/robots/" + robotId + "/state", payload);
    }

    public void robotPosition(UUID robotId, Object payload) {
        messagingTemplate.convertAndSend("/topic/robots/" + robotId + "/position", payload);
    }

    public void robotRealtime(UUID robotId, Object payload) {
        messagingTemplate.convertAndSend("/topic/robots/" + robotId + "/realtime", payload);
    }

    public void orderStatus(UUID robotId, Object payload) {
        messagingTemplate.convertAndSend("/topic/robots/" + robotId + "/order-status", payload);
    }

    public void alert(Object payload) {
        messagingTemplate.convertAndSend("/topic/alerts", payload);
    }
}
