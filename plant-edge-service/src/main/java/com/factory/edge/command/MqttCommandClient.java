package com.factory.edge.command;

import com.factory.edge.mqtt.MqttClientOptionsFactory;
import com.factory.edge.mqtt.MqttProperties;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Paho connect / subscribe / publish for the edge command client. */
final class MqttCommandClient implements MqttCallbackExtended {

    private static final int SUBACK_FAILURE_QOS = 128;
    private static final Logger log = LoggerFactory.getLogger(MqttCommandClient.class);

    private final MqttProperties properties;
    private final MqttClientOptionsFactory optionsFactory;
    private final String commandFilter;
    private final MqttClient client;
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread reconnectThread;
    private Consumer<Boolean> onConnected = reconnect -> {};
    private MessageHandler messageHandler = (t, p) -> {};

    MqttCommandClient(MqttProperties properties, String commandFilter) throws MqttException {
        this.properties = properties;
        this.optionsFactory = new MqttClientOptionsFactory(properties);
        this.commandFilter = commandFilter;
        this.client = new MqttClient(
                properties.normalizedBrokerUrl(),
                properties.getClientId() + "-commands",
                new MemoryPersistence());
        this.client.setCallback(this);
        this.reconnectThread = new Thread(this::reconnectLoop, "edge-mqtt-cmd-reconnect");
        this.reconnectThread.setDaemon(true);
    }

    void start(Consumer<Boolean> onConnected, MessageHandler messageHandler) {
        this.onConnected = onConnected == null ? r -> {} : onConnected;
        this.messageHandler = messageHandler == null ? (t, p) -> {} : messageHandler;
        reconnectThread.start();
    }

    boolean isConnected() {
        return connected.get() && client != null && client.isConnected() && subscribed.get();
    }

    String lastError() {
        return lastError.get();
    }

    boolean publish(String topic, String payload) {
        if (!client.isConnected()) {
            return false;
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(properties.getQos());
            client.publish(topic, message);
            return true;
        } catch (MqttException e) {
            connected.set(false);
            subscribed.set(false);
            lastError.set(MqttClientOptionsFactory.describeConnectFailure(e));
            log.warn("EDGE CMD ACK publish failed: {}", e.getMessage());
            return false;
        }
    }

    void shutdown() {
        closed.set(true);
        reconnectThread.interrupt();
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            log.debug("EDGE CMD MQTT shutdown: {}", e.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        connected.set(true);
        log.info("EDGE CMD MQTT connectComplete reconnect={} uri={}", reconnect, serverURI);
        subscribeCommands();
        onConnected.accept(reconnect);
    }

    @Override
    public void connectionLost(Throwable cause) {
        connected.set(false);
        subscribed.set(false);
        lastError.set(MqttClientOptionsFactory.describeConnectFailure(cause));
        log.warn("EDGE CMD MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        messageHandler.onMessage(topic, message.getPayload());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}

    private void subscribeCommands() {
        try {
            IMqttToken token = client.subscribeWithResponse(commandFilter, properties.getQos());
            int[] granted = token.getGrantedQos();
            if (granted == null || granted.length == 0 || granted[0] == SUBACK_FAILURE_QOS) {
                subscribed.set(false);
                String msg = "SUBACK 0x80 subscription rejected by broker ACL for " + commandFilter;
                lastError.set(msg);
                log.error(msg);
                return;
            }
            subscribed.set(true);
            lastError.set(null);
            log.info("EDGE CMD MQTT subscribed filter={}", commandFilter);
        } catch (MqttException e) {
            subscribed.set(false);
            String msg = MqttClientOptionsFactory.describeConnectFailure(e);
            lastError.set(msg);
            log.warn("EDGE CMD MQTT subscribe failed: {}", msg);
        }
    }

    private void reconnectLoop() {
        long delay = Math.max(100L, properties.getReconnectInitialMs());
        long maxDelay = Math.max(delay, properties.getReconnectMaxMs());
        while (!closed.get()) {
            if (client.isConnected() && subscribed.get()) {
                connected.set(true);
                sleepQuiet(delay);
                continue;
            }
            connected.set(false);
            if (!client.isConnected()) {
                subscribed.set(false);
            }
            try {
                client.connect(optionsFactory.connectOptions());
                connected.set(true);
                log.info("EDGE CMD MQTT connected broker={}", properties.normalizedBrokerUrl());
                subscribeCommands();
                onConnected.accept(false);
                delay = Math.max(100L, properties.getReconnectInitialMs());
            } catch (MqttException e) {
                lastError.set(MqttClientOptionsFactory.describeConnectFailure(e));
                log.warn("EDGE CMD MQTT broker unreachable; retry in {}ms: {}", delay, lastError.get());
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

    @FunctionalInterface
    interface MessageHandler {
        void onMessage(String topic, byte[] payload);
    }
}