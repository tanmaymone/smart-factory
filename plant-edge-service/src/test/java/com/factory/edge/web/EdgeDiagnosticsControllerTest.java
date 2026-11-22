package com.factory.edge.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.edge.mqtt.EdgeLinkHealth;
import com.factory.edge.opcua.OpcUaLinkHealth;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Tag("EDGE-UI")
@WebMvcTest(controllers = EdgeDiagnosticsController.class)
class EdgeDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EdgeStatusService statusService;

    @Test
    void statusEndpointReturnsSnapshotContract() throws Exception {
        when(statusService.snapshot())
                .thenReturn(
                        new EdgeStatusSnapshot(
                                new EdgeStatusSnapshot.OpcUaStatus(
                                        "opc.tcp://localhost:53530/OPCUA/SimulationServer",
                                        OpcUaLinkHealth.DISCONNECTED,
                                        "BACKOFF",
                                        "session lost",
                                        null),
                                new EdgeStatusSnapshot.MqttStatus(
                                        "tcp://localhost:1883",
                                        EdgeLinkHealth.DISCONNECTED,
                                        false,
                                        2,
                                        Instant.parse("2026-01-15T10:00:00Z")),
                                List.of(
                                        new EdgeStatusSnapshot.TagStatus(
                                                "INDORE",
                                                "LINE-01",
                                                "VALVE_101_POS",
                                                "ns=3;i=1001",
                                                0.5,
                                                42,
                                                "GOOD",
                                                Instant.parse("2026-01-15T10:00:01Z"),
                                                false)),
                                List.of()));

        mockMvc.perform(get("/api/edge/status").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opcua.status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.opcua.lastHeartbeatAt").value(nullValue()))
                .andExpect(jsonPath("$.mqtt.bufferedCount").value(2))
                .andExpect(jsonPath("$.mqtt.commandSubscriberConnected").value(false))
                .andExpect(jsonPath("$.tags[0].value").value(42))
                .andExpect(jsonPath("$.tags[0].emitted").value(false))
                .andExpect(jsonPath("$.commands").isArray());
    }

    @Test
    void indexPageIsServed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getForwardedUrl())
                                .isEqualTo("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Plant Edge Diagnostics")))
                .andExpect(content().string(Matchers.containsString("Last observed")))
                .andExpect(content().string(Matchers.containsString("/api/edge/status")))
                .andExpect(content().string(Matchers.containsString("/smart-factory-logo.png")));

        mockMvc.perform(get("/smart-factory-logo.png"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(
                                        result.getResponse().getContentType())
                                .startsWith("image/"));
    }
}
