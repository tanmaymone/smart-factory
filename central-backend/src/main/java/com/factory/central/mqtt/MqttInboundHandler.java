package com.factory.central.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Handles inbound MQTT messages for a single topic filter. Implementations must not throw;
 * the router catches and logs any unexpected exception so one bad payload cannot drop later messages.
 */
public interface MqttInboundHandler {

    /** MQTT topic filter (may contain {@code +} / {@code #} wildcards). */
    String topicFilter();

    void handle(String topic, MqttMessage message);
}
