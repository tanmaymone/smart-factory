package com.factory.edge.mqtt;

import com.factory.edge.buffer.LinkUnavailableException;
import com.factory.edge.buffer.TelemetryLink;
import com.factory.edge.buffer.TelemetryRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Paho MQTT v3 publisher for {@code site/{siteId}/line/{lineId}/telemetry}. Memory persistence +
 * cleanSession; SQLite remains the replay source. Broker-down at startup does not fail the app.
 */
@Component
public class MqttTelemetryLink implements TelemetryLink, MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryLink.class);

    private final MqttProperties properties;
    private final MqttClientOptionsFactory optionsFactory;
    private final TelemetryEnvelopeMapper mapper;
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<Instant> lastPublishAt = new AtomicReference<>();
    private final AtomicReference<Runnable> recovery = new AtomicReference<>(() -> {});
    private final AtomicReference<IntSupplier> bufferedCount = new AtomicReference<>(() -> 0);
    private final AtomicBoolean closed = new AtomicBoolean();

    private final MqttClient client;
    private final Thread reconnectThread;

    public MqttTelemetryLink(MqttProperties properties, ObjectMapper objectMapper) throws MqttException {
        this.properties = properties;
        this.optionsFactory = new MqttClientOptionsFactory(properties);
        this.mapper = new TelemetryEnvelopeMapper(objectMapper);
        this.client = new MqttClient(
                properties.normalizedBrokerUrl(), properties.getClientId(), new MemoryPersistence());
        this.client.setCallback(this);
        this.reconnectThread = new Thread(this::reconnectLoop, "edge-mqtt-reconnect");
        this.reconnectThread.setDaemon(true);
        this.reconnectThread.start();
    }

    @Override
    public void bindRecovery(Runnable onRecovered) {
        recovery.set(onRecovered == null ? () -> {} : onRecovered);
    }

    /** Optional live queue depth for {@link #health()}. */
    public void bindBufferedCount(IntSupplier depth) {
        bufferedCount.set(depth == null ? () -> 0 : depth);
    }

    public EdgeLinkHealth health() {
        return new EdgeLinkHealth(
                connected.get() ? EdgeLinkHealth.CONNECTED : EdgeLinkHealth.DISCONNECTED,
                bufferedCount.get().getAsInt(),
                lastPublishAt.get(),
                lastError.get());
    }

    String lastError() {
        return lastError.get();
    }

    @Override
    public boolean isAvailable() {
        return connected.get() && client.isConnected();
    }

    @Override
    public void publish(TelemetryRecord record) throws LinkUnavailableException {
        if (!isAvailable()) {
            throw new LinkUnavailableException("MQTT client is not connected");
        }
        try {
            String topic = TelemetryEnvelopeMapper.topic(record);
            String payload = mapper.toJson(record);
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(properties.getQos());
            message.setRetained(false);
            client.publish(topic, message);
            lastPublishAt.set(Instant.now());
            log.debug("EDGE MQTT published topic={} bytes={}", topic, payload.length());
        } catch (MqttException e) {
            connected.set(false);
            lastError.set(MqttClientOptionsFactory.describeConnectFailure(e));
            throw new LinkUnavailableException("MQTT publish failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        connected.set(true);
        lastError.set(null);
        log.info("EDGE MQTT connectComplete reconnect={} uri={}", reconnect, serverURI);
        recovery.get().run();
    }

    @Override
    public void connectionLost(Throwable cause) {
        connected.set(false);
        lastError.set(MqttClientOptionsFactory.describeConnectFailure(cause));
        log.warn("EDGE MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // publisher-only client
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // sync publish waits for PUBACK
    }

    private void reconnectLoop() {
        long delay = Math.max(100L, properties.getReconnectInitialMs());
        long maxDelay = Math.max(delay, properties.getReconnectMaxMs());
        while (!closed.get()) {
            if (client.isConnected()) {
                connected.set(true);
                sleepQuiet(delay);
                continue;
            }
            connected.set(false);
            try {
                client.connect(optionsFactory.connectOptions());
                connected.set(true);
                lastError.set(null);
                log.info(
                        "EDGE MQTT connected broker={} clientId={}",
                        properties.normalizedBrokerUrl(),
                        properties.getClientId());
                delay = Math.max(100L, properties.getReconnectInitialMs());
                recovery.get().run();
            } catch (MqttException e) {
                lastError.set(MqttClientOptionsFactory.describeConnectFailure(e));
                log.warn(
                        "EDGE MQTT broker unreachable at {}; retry in {}ms: {}",
                        properties.normalizedBrokerUrl(),
                        delay,
                        lastError.get());
                sleepQuiet(delay);
                delay = Math.min(maxDelay, delay * 2);
            }
        }
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdown() {
        closed.set(true);
        reconnectThread.interrupt();
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            log.debug("EDGE MQTT shutdown: {}", e.getMessage());
        }
    }
}