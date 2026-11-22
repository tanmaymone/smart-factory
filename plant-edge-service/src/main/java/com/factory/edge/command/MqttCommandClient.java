package com.factory.edge.command;

import com.factory.edge.mqtt.MqttProperties;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Paho connect / subscribe / publish for the edge command client. */
final class MqttCommandClient implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttCommandClient.class);
    private static final String COMMAND_FILTER = "site/+/line/+/commands";

    private final MqttProperties properties;
    private final MqttClient client;
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread reconnectThread;
    private Consumer<Boolean> onConnected = reconnect -> {};
    private MessageHandler messageHandler = (t, p) -> {};

    MqttCommandClient(MqttProperties properties) throws MqttException {
        this.properties = properties;
        this.client =
                new MqttClient(
                        properties.getBrokerUrl(),
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
        return connected.get() && client.isConnected();
    }

    boolean publish(String topic, String payload) {
        if (!isConnected()) {
            return false;
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(properties.getQos());
            client.publish(topic, message);
            return true;
        } catch (MqttException e) {
            connected.set(false);
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
            client.subscribe(COMMAND_FILTER, properties.getQos());
            log.info("EDGE CMD MQTT subscribed filter={}", COMMAND_FILTER);
        } catch (MqttException e) {
            log.warn("EDGE CMD MQTT subscribe failed: {}", e.getMessage());
        }
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
                client.connect(connectOptions());
                connected.set(true);
                log.info("EDGE CMD MQTT connected broker={}", properties.getBrokerUrl());
                subscribeCommands();
                onConnected.accept(false);
                delay = Math.max(100L, properties.getReconnectInitialMs());
            } catch (MqttException e) {
                log.warn("EDGE CMD MQTT broker unreachable; retry in {}ms: {}", delay, e.getMessage());
                sleepQuiet(delay);
                delay = Math.min(maxDelay, delay * 2);
            }
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {properties.getBrokerUrl()});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setMaxReconnectDelay(
                (int) Math.min(Integer.MAX_VALUE, properties.getReconnectMaxMs()));
        return options;
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
