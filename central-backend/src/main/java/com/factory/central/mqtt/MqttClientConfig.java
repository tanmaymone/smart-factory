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
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(buildOptions(properties));
        return factory;
    }

    static MqttConnectOptions buildOptions(MqttProperties properties) {
        String brokerUrl = properties.normalizedBrokerUrl();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {brokerUrl});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername().trim());
            options.setPassword(properties.getPassword().toCharArray());
        }

        if (properties.isTls()) {
            MqttProperties.Tls tls = properties.getTls();
            options.setSocketFactory(MqttTlsSupport.buildSocketFactory(tls));
            options.setHttpsHostnameVerificationEnabled(tls.isVerifyHostname());
        }

        return options;
    }

    @Bean(destroyMethod = "")
    IMqttClient mqttClient(DefaultMqttPahoClientFactory mqttClientFactory, MqttProperties properties)
            throws MqttException {
        String brokerUrl = properties.normalizedBrokerUrl();
        this.mqttClient = mqttClientFactory.getClientInstance(brokerUrl, properties.getClientId());
        try {
            mqttClient.connect(mqttClientFactory.getConnectionOptions());
            log.info(
                    "MQTT client connected to {} (clientId={}, qos={}, tls={})",
                    brokerUrl,
                    properties.getClientId(),
                    properties.getQos(),
                    properties.isTls());
        } catch (MqttException ex) {
            log.warn(
                    "MQTT broker unreachable at {}; registry API will still start. {}",
                    brokerUrl,
                    MqttTlsSupport.describeMqttException(ex));
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