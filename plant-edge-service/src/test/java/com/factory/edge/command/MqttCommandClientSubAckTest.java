package com.factory.edge.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.mqtt.MqttProperties;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-M41")
class MqttCommandClientSubAckTest {

    @Test
    void connectionLostClearsSubscribedFlag() throws Exception {
        MqttProperties properties = new MqttProperties();
        properties.setBrokerUrl("tcp://127.0.0.1:1");
        properties.setClientId("edge-cmd-suback-test");

        MqttCommandClient client =
                new MqttCommandClient(properties, "site/INDORE/line/+/commands");
        try {
            assertThat(client.isConnected()).isFalse();

            client.connectionLost(new Exception("broker down"));
            assertThat(client.isConnected()).isFalse();
            assertThat(client.lastError()).isNotBlank();

            client.messageArrived("site/INDORE/line/LINE-01/commands", new MqttMessage("{}".getBytes()));
            assertThat(client.isConnected()).isFalse();

            client.connectComplete(true, properties.getBrokerUrl());
            assertThat(client.isConnected()).isFalse();
        } finally {
            client.shutdown();
        }
    }
}