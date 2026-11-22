package com.factory.central.stream;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(StreamProperties.class)
public class StreamConfiguration implements WebMvcConfigurer {

    private final StreamProperties streamProperties;
    private final TelemetryStreamHub hub;

    public StreamConfiguration(StreamProperties streamProperties, TelemetryStreamHub hub) {
        this.streamProperties = streamProperties;
        this.hub = hub;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(streamProperties.getEmitterTimeoutMs());
    }

    @Scheduled(fixedDelayString = "${app.stream.heartbeat-ms:15000}")
    void heartbeat() {
        hub.heartbeat();
    }
}
