package com.factory.central.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.LatestTagCache;
import com.factory.central.cache.LatestTagState;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("SF-M1.6")
class CommandApiIT {

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
        registry.add("app.mqtt.client-id", () -> "central-backend-cmd-it-" + UUID.randomUUID());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommandAuditLogRepository auditLogRepository;

    @Autowired
    private LatestTagCache latestTagCache;

    @Test
    void acceptedCommandPublishesMqttAndAcksToAudit() throws Exception {
        CopyOnWriteArrayList<String> published = new CopyOnWriteArrayList<>();
        MqttClient client = testClient("cmd-sub-" + UUID.randomUUID());
        try {
            client.subscribe(
                    "site/INDORE/line/LINE-01/commands",
                    1,
                    (IMqttMessageListener) (topic, message) ->
                            published.add(new String(message.getPayload(), StandardCharsets.UTF_8)));

            MvcResult result = mockMvc.perform(post("/api/commands")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "siteId": "INDORE",
                                      "lineId": "LINE-01",
                                      "tagCode": "VALVE_101_POS",
                                      "targetValue": 55.0,
                                      "requestedBy": "operator@factory.com",
                                      "reason": "open inlet valve"
                                    }
                                    """))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("VALIDATED_DISPATCHED"))
                    .andReturn();

            String commandId = objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("commandId")
                    .asText();

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published).isNotEmpty());
            JsonNode payload = objectMapper.readTree(published.getFirst());
            assertThat(payload.get("commandId").asText()).isEqualTo(commandId);
            assertThat(payload.get("tagCode").asText()).isEqualTo("VALVE_101_POS");
            assertThat(payload.get("targetValue").asDouble()).isEqualTo(55.0);

            publishAck(commandId, "ACKED", 55.0, null);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                CommandAuditLog row = auditLogRepository
                        .findByCommandId(UUID.fromString(commandId))
                        .orElseThrow();
                assertThat(row.getStatus()).isEqualTo(CommandStatus.ACKED);
                assertThat(row.getAppliedValue()).isEqualTo("55.0");
            });

            mockMvc.perform(get("/api/commands/audit")
                            .param("siteId", "INDORE")
                            .param("lineId", "LINE-01")
                            .param("status", "ACKED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].commandId").value(commandId))
                    .andExpect(jsonPath("$[0].status").value("ACKED"));

            Instant firstAckedAt = auditLogRepository
                    .findByCommandId(UUID.fromString(commandId))
                    .orElseThrow()
                    .getAckedAt();
            publishAck(commandId, "ACKED", 99.0, null);
            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                CommandAuditLog row = auditLogRepository
                        .findByCommandId(UUID.fromString(commandId))
                        .orElseThrow();
                assertThat(row.getAppliedValue()).isEqualTo("55.0");
                assertThat(row.getAckedAt()).isEqualTo(firstAckedAt);
            });
        } finally {
            closeQuietly(client);
        }
    }

    @Test
    void outOfRangeAndNotWritablePersistRejectedWithoutPublish() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MqttClient client = testClient("cmd-reject-" + UUID.randomUUID());
        try {
            client.subscribe(
                    "site/INDORE/line/+/commands",
                    1,
                    (topic, message) -> seen.set(new String(message.getPayload(), StandardCharsets.UTF_8)));

            mockMvc.perform(post("/api/commands")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "siteId": "INDORE",
                                      "lineId": "LINE-01",
                                      "tagCode": "VALVE_101_POS",
                                      "targetValue": 150.0,
                                      "requestedBy": "operator@factory.com",
                                      "reason": "too high"
                                    }
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("TARGET_OUT_OF_RANGE"))
                    .andExpect(jsonPath("$.commandId").exists());

            mockMvc.perform(post("/api/commands")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "siteId": "INDORE",
                                      "lineId": "LINE-01",
                                      "tagCode": "REACTOR_TEMP_C",
                                      "targetValue": 40.0,
                                      "requestedBy": "operator@factory.com",
                                      "reason": "read-only"
                                    }
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("TAG_NOT_WRITABLE"))
                    .andExpect(jsonPath("$.commandId").exists());

            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                assertThat(seen.get()).isNull();
                assertThat(auditLogRepository.findAll())
                        .anyMatch(row -> row.getStatus() == CommandStatus.REJECTED_VALIDATION
                                && "VALVE_101_POS".equals(row.getTagCode()))
                        .anyMatch(row -> row.getStatus() == CommandStatus.REJECTED_VALIDATION
                                && "REACTOR_TEMP_C".equals(row.getTagCode()));
            });
        } finally {
            closeQuietly(client);
        }
    }

    @Test
    void interlockRejectionAndMalformedAckDoesNotBreakRouter() throws Exception {
        JsonNode tags = objectMapper.readTree(
                mockMvc.perform(get("/api/registry/tags")
                                .param("siteId", "INDORE")
                                .param("lineId", "LINE-01"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode sp = null;
        for (JsonNode tag : tags) {
            if ("REACTOR_TEMP_SP".equals(tag.get("tagCode").asText())) {
                sp = tag;
                break;
            }
        }
        assertThat(sp).isNotNull();
        String tagId = sp.get("id").asText();

        mockMvc.perform(put("/api/registry/tags/" + tagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registryPutBody(sp, InletValveOpenInterlockRule.KEY))))
                .andExpect(status().isOk());

        latestTagCache.put(LatestTagState.initial(
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                2.0,
                "GOOD",
                Instant.parse("2026-11-19T12:00:00Z"),
                AlarmSeverity.NONE,
                null));

        mockMvc.perform(post("/api/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "siteId": "INDORE",
                                  "lineId": "LINE-01",
                                  "tagCode": "REACTOR_TEMP_SP",
                                  "targetValue": 40.0,
                                  "requestedBy": "operator@factory.com",
                                  "reason": "interlock check"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INTERLOCK_REJECTED"))
                .andExpect(jsonPath("$.commandId").exists());

        MqttClient client = testClient("ack-malformed-" + UUID.randomUUID());
        try {
            client.publish(
                    "site/INDORE/line/LINE-01/commands/acks",
                    new MqttMessage("{not-json".getBytes(StandardCharsets.UTF_8)));

            String commandId = UUID.randomUUID().toString();
            auditLogRepository.save(new CommandAuditLog(
                    UUID.fromString(commandId),
                    "INDORE",
                    "LINE-01",
                    "VALVE_101_POS",
                    "40.0",
                    null,
                    "operator@factory.com",
                    "manual",
                    CommandStatus.VALIDATED_DISPATCHED,
                    null,
                    Instant.parse("2026-11-19T12:00:00Z")));

            publishAck(commandId, "ACKED", 40.0, null);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(auditLogRepository
                            .findByCommandId(UUID.fromString(commandId))
                            .orElseThrow()
                            .getStatus())
                    .isEqualTo(CommandStatus.ACKED));
        } finally {
            closeQuietly(client);
        }
    }

    @Test
    void whitespaceTagCodeIsBadRequestWithoutAudit() throws Exception {
        long before = auditLogRepository.count();
        mockMvc.perform(post("/api/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "siteId": "INDORE",
                                  "lineId": "LINE-01",
                                  "tagCode": "   ",
                                  "targetValue": 10.0,
                                  "requestedBy": "operator@factory.com",
                                  "reason": "blank tag"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    private void publishAck(String commandId, String status, Object appliedValue, String errorMessage)
            throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "commandId",
                commandId,
                "siteId",
                "INDORE",
                "lineId",
                "LINE-01",
                "tagCode",
                "VALVE_101_POS",
                "status",
                status,
                "appliedValue",
                appliedValue,
                "errorMessage",
                errorMessage == null ? "" : errorMessage,
                "executedAt",
                "2026-11-19T12:05:00.145Z"));
        // empty errorMessage as "" — use JsonNode for null
        JsonNode node = objectMapper.readTree(payload);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                .putNull("errorMessage");
        if (errorMessage != null) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("errorMessage", errorMessage);
        }
        MqttClient client = testClient("ack-pub-" + UUID.randomUUID());
        try {
            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(node));
            message.setQos(1);
            client.publish("site/INDORE/line/LINE-01/commands/acks", message);
            client.disconnect();
        } finally {
            closeQuietly(client);
        }
    }

    private MqttClient testClient(String clientId) throws Exception {
        String broker = "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
        MqttClient client = new MqttClient(broker, clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        client.connect(options);
        return client;
    }

    private static void closeQuietly(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            client.disconnectForcibly(1000, 1000);
        } catch (Exception ignored) {
            // best-effort disconnect before close
        }
        try {
            client.close(true);
        } catch (Exception ignored) {
            // ignore close races in tests
        }
    }

    private static java.util.Map<String, Object> registryPutBody(JsonNode sp, String interlockKey) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("siteId", sp.get("siteId").asText());
        body.put("lineId", sp.get("lineId").asText());
        body.put("tagCode", sp.get("tagCode").asText());
        body.put("description", sp.get("description").asText());
        body.put("protocol", sp.get("protocol").asText());
        body.put("endpointUrl", sp.get("endpointUrl").asText());
        body.put("externalAddress", sp.get("externalAddress").asText());
        body.put("dataType", sp.get("dataType").asText());
        body.put("unit", sp.get("unit").asText());
        body.put("writable", sp.get("writable").asBoolean());
        body.put("minValue", sp.get("minValue").asDouble());
        body.put("maxValue", sp.get("maxValue").asDouble());
        body.put("deadband", sp.get("deadband").asDouble());
        body.put("warningLow", sp.get("warningLow").isNull() ? null : sp.get("warningLow").asDouble());
        body.put("warningHigh", sp.get("warningHigh").isNull() ? null : sp.get("warningHigh").asDouble());
        body.put("criticalLow", sp.get("criticalLow").isNull() ? null : sp.get("criticalLow").asDouble());
        body.put("criticalHigh", sp.get("criticalHigh").isNull() ? null : sp.get("criticalHigh").asDouble());
        body.put("interlockRuleKey", interlockKey);
        return body;
    }
}
