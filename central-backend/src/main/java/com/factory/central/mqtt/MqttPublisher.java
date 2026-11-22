package com.factory.central.mqtt;

import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisher.class);

    private final IMqttClient mqttClient;
    private final MqttProperties mqttProperties;

    public MqttPublisher(IMqttClient mqttClient, MqttProperties mqttProperties) {
        this.mqttClient = mqttClient;
        this.mqttProperties = mqttProperties;
    }

    public void publish(String topic, String payload) {
        if (!mqttClient.isConnected()) {
            throw new MqttPublishException("MQTT client is not connected");
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(mqttProperties.getQos());
            message.setRetained(false);
            mqttClient.publish(topic, message);
            log.debug("Published MQTT topic={} bytes={}", topic, payload.length());
        } catch (MqttException ex) {
            throw new MqttPublishException("MQTT publish failed: " + ex.getMessage(), ex);
        }
    }
}
