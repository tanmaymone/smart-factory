package com.factory.central.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

@Tag("SF-M1.5")
class StreamMvcTimeoutTest {

    @Test
    void asyncTimeoutMatchesDefaultEmitterTimeout() {
        StreamProperties properties = new StreamProperties();
        assertThat(properties.getEmitterTimeoutMs()).isEqualTo(1_800_000L);

        TelemetryStreamHub hub = mock(TelemetryStreamHub.class);
        StreamConfiguration configuration = new StreamConfiguration(properties, hub);
        AsyncSupportConfigurer configurer = mock(AsyncSupportConfigurer.class);

        configuration.configureAsyncSupport(configurer);

        verify(configurer).setDefaultTimeout(properties.getEmitterTimeoutMs());
    }

    @Test
    void asyncTimeoutTracksOverriddenEmitterTimeoutProperty() {
        StreamProperties properties = new StreamProperties();
        properties.setEmitterTimeoutMs(45_000L);

        TelemetryStreamHub hub = mock(TelemetryStreamHub.class);
        StreamConfiguration configuration = new StreamConfiguration(properties, hub);
        AsyncSupportConfigurer configurer = mock(AsyncSupportConfigurer.class);

        configuration.configureAsyncSupport(configurer);

        verify(configurer).setDefaultTimeout(45_000L);
        assertThat(properties.getEmitterTimeoutMs()).isEqualTo(45_000L);
    }
}
