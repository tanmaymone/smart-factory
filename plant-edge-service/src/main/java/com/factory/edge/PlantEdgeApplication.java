package com.factory.edge;

import com.factory.edge.buffer.BufferProperties;
import com.factory.edge.config.EdgeConfigProperties;
import com.factory.edge.mqtt.MqttProperties;
import com.factory.edge.onboarding.OnboardingProbeProperties;
import com.factory.edge.opcua.OpcUaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    OpcUaProperties.class,
    BufferProperties.class,
    MqttProperties.class,
    OnboardingProbeProperties.class,
    EdgeConfigProperties.class
})
public class PlantEdgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlantEdgeApplication.class, args);
    }
}
