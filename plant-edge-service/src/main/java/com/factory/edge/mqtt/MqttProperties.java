package com.factory.edge.mqtt;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mqtt")
public class MqttProperties {

    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "plant-edge";
    private int qos = 1;
    private long reconnectInitialMs = 1000;
    private long reconnectMaxMs = 30000;
    private String username = "";
    private String password = "";
    private Tls tls = new Tls();

    @PostConstruct
    void validate() {
        String normalized = normalizedBrokerUrl();
        if (!normalized.startsWith("tcp://") && !normalized.startsWith("ssl://")) {
            throw new IllegalStateException(
                    "app.mqtt.broker-url must use tcp://, ssl://, or tls:// but was: " + brokerUrl);
        }
        boolean userBlank = username == null || username.isBlank();
        boolean passBlank = password == null || password.isBlank();
        if (userBlank ^ passBlank) {
            throw new IllegalStateException(
                    "app.mqtt.username and app.mqtt.password must both be set or both be empty");
        }
        String ca = tls == null ? "" : tls.getCaCertPath();
        if (ca != null && !ca.isBlank()) {
            Path path = Path.of(ca.trim());
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalStateException("MQTT CA cert path is not readable: " + ca);
            }
        }
    }

    public String normalizedBrokerUrl() {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            return "tcp://localhost:1883";
        }
        String trimmed = brokerUrl.trim();
        if (trimmed.regionMatches(true, 0, "tls://", 0, 6)) {
            return "ssl://" + trimmed.substring(6);
        }
        return trimmed;
    }

    public boolean isTls() {
        return normalizedBrokerUrl().regionMatches(true, 0, "ssl://", 0, 6);
    }

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

    public Tls getTls() {
        return tls;
    }

    public void setTls(Tls tls) {
        this.tls = tls == null ? new Tls() : tls;
    }

    public static class Tls {
        private String caCertPath = "";
        private boolean verifyHostname = true;

        public String getCaCertPath() {
            return caCertPath;
        }

        public void setCaCertPath(String caCertPath) {
            this.caCertPath = caCertPath == null ? "" : caCertPath;
        }

        public boolean isVerifyHostname() {
            return verifyHostname;
        }

        public void setVerifyHostname(boolean verifyHostname) {
            this.verifyHostname = verifyHostname;
        }
    }
}