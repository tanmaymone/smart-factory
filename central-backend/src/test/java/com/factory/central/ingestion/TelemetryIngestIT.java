package com.factory.central.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("SF-M1.4")
class TelemetryIngestIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse("timescale/timescaledb:latest-pg16")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("smart_factory")
            .withUsername("smartfactory")
            .withPassword("smartfactory");

    @Container
    static GenericContainer<?> mosquitto = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
            .withExposedPorts(1883)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("mosquitto-test.conf"),
                    "/mosquitto/config/mosquitto.conf");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
                "app.mqtt.broker-url",
                () -> "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883));
        registry.add("app.mqtt.client-id", () -> "central-backend-it-" + UUID.randomUUID());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void httpIngestUpdatesHistoryAlarmsAndSupportsPartialFailures() throws Exception {
        String critical =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T12:00:00Z",
                  "readings": [
                    {
                      "tagCode": "REACTOR_TEMP_C",
                      "value": 85.0,
                      "quality": "GOOD",
                      "sourceTimestamp": "2026-11-19T17:30:00+05:30"
                    }
                  ]
                }
                """;
        mockMvc.perform(post("/api/telemetry/ingest").contentType(MediaType.APPLICATION_JSON).content(critical))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));

        mockMvc.perform(get("/api/telemetry/alarms").param("siteId", "INDORE").param("lineId", "LINE-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tagCode").value("REACTOR_TEMP_C"))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].alarmFrozen").value(false));

        mockMvc.perform(
                        get("/api/telemetry/history")
                                .param("siteId", "INDORE")
                                .param("lineId", "LINE-01")
                                .param("tagCode", "REACTOR_TEMP_C")
                                .param("from", "2026-11-19T11:59:59Z")
                                .param("to", "2026-11-19T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(
                        get("/api/telemetry/history")
                                .param("siteId", "INDORE")
                                .param("lineId", "LINE-01")
                                .param("tagCode", "REACTOR_TEMP_C")
                                .param("from", "2026-11-19T12:00:00Z")
                                .param("to", "2026-11-19T12:00:01Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numericValue").value(85.0))
                .andExpect(jsonPath("$[0].time").value("2026-11-19T12:00:00Z"));

        String cleared =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "tagCode": "REACTOR_TEMP_C",
                  "value": 50.0,
                  "quality": "GOOD",
                  "sourceTimestamp": "2026-11-19T12:01:00Z"
                }
                """;
        mockMvc.perform(post("/api/telemetry/ingest").contentType(MediaType.APPLICATION_JSON).content(cleared))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/telemetry/alarms").param("siteId", "INDORE").param("lineId", "LINE-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        String bad =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "tagCode": "REACTOR_TEMP_C",
                  "value": 85.0,
                  "quality": "GOOD",
                  "sourceTimestamp": "2026-11-19T12:02:00Z"
                }
                """;
        mockMvc.perform(post("/api/telemetry/ingest").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isAccepted());
        String freeze =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "tagCode": "REACTOR_TEMP_C",
                  "value": 40.0,
                  "quality": "BAD",
                  "sourceTimestamp": "2026-11-19T12:03:00Z"
                }
                """;
        mockMvc.perform(post("/api/telemetry/ingest").contentType(MediaType.APPLICATION_JSON).content(freeze))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/telemetry/alarms").param("siteId", "INDORE").param("lineId", "LINE-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].alarmFrozen").value(true))
                .andExpect(jsonPath("$[0].quality").value("BAD"));

        String partial =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T12:04:00Z",
                  "readings": [
                    { "tagCode": "VALVE_101_POS", "value": 10.0, "quality": "GOOD" },
                    { "tagCode": "MISSING_TAG", "value": 1.0, "quality": "GOOD" }
                  ]
                }
                """;
        mockMvc.perform(post("/api/telemetry/ingest").contentType(MediaType.APPLICATION_JSON).content(partial))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.errors[0].index").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("TAG_NOT_FOUND"));

        String allInvalid =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T12:05:00Z",
                  "readings": [
                    { "tagCode": "VALVE_101_POS", "value": "nope", "quality": "GOOD" },
                    { "tagCode": "REACTOR_TEMP_C", "value": "nope", "quality": "GOOD" }
                  ]
                }
                """;
        mockMvc.perform(post("/api/telemetry/ingest").contentType(MediaType.APPLICATION_JSON).content(allInvalid))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.errors.length()").value(2));

        String mixed =
                """
                {
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T12:06:00Z",
                  "readings": [
                    { "tagCode": "MISSING_A", "value": 1.0, "quality": "GOOD" },
                    { "tagCode": "VALVE_101_POS", "value": "nope", "quality": "GOOD" }
                  ]
                }
                """;
        mockMvc.perform(post("/api/telemetry/ingest").contentType(MediaType.APPLICATION_JSON).content(mixed))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INGEST_REJECTED"));
    }

    @Test
    void mqttIngestUpdatesLatestState() throws Exception {
        String payload =
                """
                {
                  "messageId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                  "siteId": "INDORE",
                  "lineId": "LINE-01",
                  "timestamp": "2026-11-19T12:00:01.250Z",
                  "readings": [
                    {
                      "tagCode": "VALVE_101_POS",
                      "value": 75.5,
                      "dataType": "FLOAT",
                      "quality": "GOOD",
                      "sourceTimestamp": "2026-11-19T12:00:01.200Z"
                    }
                  ]
                }
                """;

        String broker = "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
        try (MqttClient client = new MqttClient(broker, "telemetry-it-publisher-" + UUID.randomUUID())) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            client.connect(options);
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish("site/INDORE/line/LINE-01/telemetry", message);
            client.disconnect();
        }

        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> mockMvc.perform(
                                get("/api/telemetry/latest")
                                        .param("siteId", "INDORE")
                                        .param("lineId", "LINE-01")
                                        .param("tagCode", "VALVE_101_POS"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].value").value(75.5))
                        .andExpect(jsonPath("$[0].quality").value("GOOD")));
    }
}
