package com.factory.edge.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mqtt")
public class MqttProperties {

    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "plant-edge";
    private int qos = 1;
    private long reconnectInitialMs = 1000;
    private long reconnectMaxMs = 30000;

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public long getReconnectInitialMs() {
        return reconnectInitialMs;
    }

    public void setReconnectInitialMs(long reconnectInitialMs) {
        this.reconnectInitialMs = reconnectInitialMs;
    }

    public long getReconnectMaxMs() {
        return reconnectMaxMs;
    }

    public void setReconnectMaxMs(long reconnectMaxMs) {
        this.reconnectMaxMs = reconnectMaxMs;
    }
}
