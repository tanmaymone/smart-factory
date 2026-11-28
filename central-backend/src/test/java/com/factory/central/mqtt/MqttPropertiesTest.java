package com.factory.central.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("GW-M41")
class MqttPropertiesTest {

    @Test
    void normalizesTlsSchemeToSsl() {
        MqttProperties props = new MqttProperties();
        props.setBrokerUrl("tls://broker:8883");
        assertThat(props.normalizedBrokerUrl()).isEqualTo("ssl://broker:8883");
        assertThat(props.isTls()).isTrue();
    }

    @Test
    void rejectsHalfConfiguredCredentials() {
        MqttProperties props = new MqttProperties();
        props.setBrokerUrl("tcp://localhost:1883");
        props.setUsername("user");
        props.setPassword("");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username and app.mqtt.password");
    }
}