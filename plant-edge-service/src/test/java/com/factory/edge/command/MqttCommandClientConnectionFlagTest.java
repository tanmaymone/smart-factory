package com.factory.edge.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.edge.mqtt.MqttProperties;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-UI")
class MqttCommandClientConnectionFlagTest {

    @Test
    void connectionLostClearsFlagAndMessageArrivedDoesNotSetIt() throws Exception {
        MqttProperties properties = new MqttProperties();
        properties.setBrokerUrl("tcp://127.0.0.1:1");
        properties.setClientId("edge-cmd-flag-test");

        MqttCommandClient client =
                new MqttCommandClient(properties, "site/INDORE/line/+/commands");
        try {
            assertThat(client.isConnected()).isFalse();

            client.connectionLost(new Exception("broker down"));
            assertThat(client.isConnected()).isFalse();

            client.messageArrived("site/INDORE/line/LINE-01/commands", new MqttMessage("{}".getBytes()));
            assertThat(client.isConnected()).isFalse();

            client.connectComplete(true, properties.getBrokerUrl());
            // Paho client itself was never connected — conjunction stays false.
            assertThat(client.isConnected()).isFalse();
        } finally {
            client.shutdown();
        }
    }
}
