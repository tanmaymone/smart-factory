package com.factory.edge.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-M41")
class MqttPropertiesTest {

    @Test
    void normalizesTlsSchemeToSsl() {
        MqttProperties props = new MqttProperties();
        props.setBrokerUrl("TLS://mosquitto:8883");
        assertThat(props.normalizedBrokerUrl()).isEqualTo("ssl://mosquitto:8883");
    }

    @Test
    void rejectsHalfConfiguredCredentials() {
        MqttProperties props = new MqttProperties();
        props.setBrokerUrl("ssl://localhost:8883");
        props.setUsername("");
        props.setPassword("secret");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username and app.mqtt.password");
    }
}