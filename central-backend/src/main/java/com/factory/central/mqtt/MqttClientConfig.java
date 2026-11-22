package com.factory.central.mqtt;

import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;

@Configuration
@EnableConfigurationProperties(MqttProperties.class)
public class MqttClientConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttClientConfig.class);

    private IMqttClient mqttClient;

    @Bean
    DefaultMqttPahoClientFactory mqttClientFactory(MqttProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {properties.getBrokerUrl()});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean(destroyMethod = "")
    IMqttClient mqttClient(DefaultMqttPahoClientFactory mqttClientFactory, MqttProperties properties)
            throws MqttException {
        this.mqttClient = mqttClientFactory.getClientInstance(properties.getBrokerUrl(), properties.getClientId());
        try {
            mqttClient.connect(mqttClientFactory.getConnectionOptions());
            log.info(
                    "MQTT client connected to {} (clientId={}, qos={})",
                    properties.getBrokerUrl(),
                    properties.getClientId(),
                    properties.getQos());
        } catch (MqttException ex) {
            log.warn(
                    "MQTT broker unreachable at {}; registry API will still start. cause={}",
                    properties.getBrokerUrl(),
                    ex.getMessage());
        }
        return mqttClient;
    }

    @PreDestroy
    void disconnect() {
        if (mqttClient == null) {
            return;
        }
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            mqttClient.close();
        } catch (MqttException ex) {
            log.debug("MQTT disconnect failed: {}", ex.getMessage());
        }
    }
}
