package com.robotmanagement.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.middleware.emqx.mqtt")
public class MqttProperties {

    private boolean enabled;

    private String host;

    private int port = 1883;

    private String username;

    private String password;

    private boolean sslEnabled;

    private String clientId = "robot-management-backend";

    private String inboundTopic = "uagv/v3/#";

    private int qos;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getInboundTopic() {
        return inboundTopic;
    }

    public void setInboundTopic(String inboundTopic) {
        this.inboundTopic = inboundTopic;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public boolean isUsable() {
        return enabled
            && host != null
            && !host.isBlank()
            && !host.startsWith("<")
            && port > 0;
    }

    public String brokerUri() {
        String scheme = sslEnabled ? "ssl" : "tcp";
        return scheme + "://" + host + ":" + port;
    }
}
