package com.factory.central.mqtt;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.stereotype.Component;

/**
 * Sole {@link MqttCallbackExtended} on the shared Paho client. Subscribes every registered
 * {@link MqttInboundHandler} filter on connect and on {@code connectComplete}.
 */
@Component
public class MqttInboundRouter implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttInboundRouter.class);

    private final IMqttClient mqttClient;
    private final DefaultMqttPahoClientFactory mqttClientFactory;
    private final MqttProperties mqttProperties;
    private final List<MqttInboundHandler> handlers;

    public MqttInboundRouter(
            IMqttClient mqttClient,
            DefaultMqttPahoClientFactory mqttClientFactory,
            MqttProperties mqttProperties,
            List<MqttInboundHandler> handlers) {
        this.mqttClient = mqttClient;
        this.mqttClientFactory = mqttClientFactory;
        this.mqttProperties = mqttProperties;
        this.handlers = List.copyOf(handlers);
    }

    @PostConstruct
    void start() {
        mqttClient.setCallback(this);
        if (mqttClient.isConnected()) {
            subscribeAll();
        } else {
            try {
                mqttClient.connect(mqttClientFactory.getConnectionOptions());
                log.info("MQTT client connected for inbound routing");
            } catch (MqttException ex) {
                log.warn(
                        "MQTT broker unreachable; inbound router will wait for reconnect: {}",
                        ex.getMessage());
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT connectComplete reconnect={} uri={}", reconnect, serverURI);
        subscribeAll();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        for (MqttInboundHandler handler : handlers) {
            if (!topicMatches(handler.topicFilter(), topic)) {
                continue;
            }
            try {
                handler.handle(topic, message);
            } catch (Exception ex) {
                log.warn(
                        "MQTT handler {} failed for topic={}: {}",
                        handler.getClass().getSimpleName(),
                        topic,
                        ex.getMessage());
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // publisher path unused by inbound router
    }

    private void subscribeAll() {
        for (MqttInboundHandler handler : handlers) {
            String filter = handler.topicFilter();
            try {
                mqttClient.subscribe(filter, mqttProperties.getQos());
                log.info("Subscribed to MQTT topic {} qos={}", filter, mqttProperties.getQos());
            } catch (MqttException ex) {
                log.warn("Failed to subscribe to {}: {}", filter, ex.getMessage());
            }
        }
    }

    /** Matches a topic against an MQTT filter that may contain {@code +} and {@code #}. */
    static boolean topicMatches(String filter, String topic) {
        if (filter == null || topic == null) {
            return false;
        }
        String[] filterParts = filter.split("/", -1);
        String[] topicParts = topic.split("/", -1);
        int fi = 0;
        int ti = 0;
        while (fi < filterParts.length && ti < topicParts.length) {
            String part = filterParts[fi];
            if ("#".equals(part)) {
                return fi == filterParts.length - 1;
            }
            if (!"+".equals(part) && !part.equals(topicParts[ti])) {
                return false;
            }
            fi++;
            ti++;
        }
        if (fi < filterParts.length && "#".equals(filterParts[fi])) {
            return fi == filterParts.length - 1;
        }
        return fi == filterParts.length && ti == topicParts.length;
    }
}
