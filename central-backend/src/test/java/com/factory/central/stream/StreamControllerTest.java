package com.factory.central.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.factory.central.cache.InMemoryLatestTagCache;
import com.factory.central.registry.RegistryLivenessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@Tag("SF-M1.5")
class StreamControllerTest {

    private TelemetryStreamHub hub;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StreamProperties properties = new StreamProperties();
        properties.setMaxSubscribers(1);
        RegistryLivenessService liveness = mock(RegistryLivenessService.class);
        when(liveness.filterLive(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        hub = new TelemetryStreamHub(
                properties, new InMemoryLatestTagCache(), liveness, new SimpleMeterRegistry());
        mockMvc = MockMvcBuilders.standaloneSetup(new StreamController(hub))
                .setControllerAdvice(new StreamExceptionHandler(new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    @AfterEach
    void tearDown() {
        hub.shutdown();
    }

    @Test
    void telemetryStreamStartsAsyncEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/stream/telemetry").param("siteId", "INDORE"))
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(result.getRequest().getAsyncContext()).isNotNull();
        assertThat(hub.subscriberCount()).isEqualTo(1);
    }

    @Test
    void capacityExceededReturns503WithStableCode() throws Exception {
        mockMvc.perform(get("/api/stream/telemetry")).andExpect(request().asyncStarted());

        MvcResult rejected = mockMvc.perform(get("/api/stream/telemetry").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        assertThat(rejected.getResponse().getContentAsString()).contains(StreamErrorCode.STREAM_AT_CAPACITY);
        assertThat(hub.subscriberCount()).isEqualTo(1);
    }
}
