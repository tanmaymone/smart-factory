package com.factory.central.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.central.command.CommandAuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
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
@Tag("SF-ACCESS")
class ActorAccessIT {

    private static final String TOKEN = "it-bootstrap-token";

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
        registry.add("app.mqtt.client-id", () -> "central-backend-access-it-" + UUID.randomUUID());
        registry.add("app.bootstrap.token", () -> TOKEN);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActorRepository actorRepository;

    @Autowired
    private CommandAuditLogRepository auditLogRepository;

    @Test
    void enforcesRolesAndDerivesAuditIdentity() throws Exception {
        seedActor("viewer@factory.com", "Viewer", ActorRole.VIEWER);
        seedActor("operator@factory.com", "Operator", ActorRole.OPERATOR);

        mockMvc.perform(post("/api/registry/sites")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "viewer@factory.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"NASHIK\",\"name\":\"Nashik\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        mockMvc.perform(post("/api/registry/sites")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "operator@factory.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"NASHIK\",\"name\":\"Nashik\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/registry/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"NASHIK\",\"name\":\"Nashik\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value(AccessPolicy.UNAUTHENTICATED_MESSAGE))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        mockMvc.perform(post("/api/registry/sites")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"NASHIK\",\"name\":\"Nashik\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AccessPolicy.UNAUTHENTICATED_MESSAGE));

        long beforeWrongToken = auditLogRepository.count();
        mockMvc.perform(post("/api/commands")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, "wrong-token")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "operator@factory.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "siteId": "INDORE",
                                  "lineId": "LINE-01",
                                  "tagCode": "VALVE_101_POS",
                                  "targetValue": 10.0,
                                  "requestedBy": "spoof",
                                  "reason": "bad token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/registry/sites")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, "wrong-token")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "operator@factory.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"NASHIK\",\"name\":\"Nashik\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(auditLogRepository.count()).isEqualTo(beforeWrongToken);

        mockMvc.perform(post("/api/registry/sites")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"NASHIK\",\"name\":\"Nashik\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/access/actors")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"actorId\":\"op@plant\",\"displayName\":\"Plant Operator\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actorId").value("op@plant"));

        mockMvc.perform(post("/api/access/actors")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"actorId\":\"sup@plant\",\"displayName\":\"Plant Supervisor\",\"role\":\"SUPERVISOR\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/access/actors")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"actorId\":\"op@plant\",\"displayName\":\"Updated Operator\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Operator"));

        mockMvc.perform(post("/api/commands")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "OP@plant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "siteId": "INDORE",
                                  "lineId": "LINE-01",
                                  "tagCode": "VALVE_101_POS",
                                  "targetValue": 10.0,
                                  "requestedBy": "spoof",
                                  "reason": "case mismatch"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AccessPolicy.UNAUTHENTICATED_MESSAGE));

        mockMvc.perform(get("/api/access/actors").header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.actorId=='op@plant')]").exists())
                .andExpect(jsonPath("$[?(@.actorId=='sup@plant')]").exists());

        mockMvc.perform(post("/api/access/actors")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"actorId\":\"admin-row\",\"displayName\":\"Nope\",\"role\":\"ONBOARDING_ADMIN\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("role"));

        mockMvc.perform(post("/api/access/actors")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":\"   \",\"displayName\":\"Blank\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("actorId"));

        mockMvc.perform(post("/api/access/actors")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "operator@factory.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"actorId\":\"x@plant\",\"displayName\":\"X\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/access/actors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"actorId\":\"y@plant\",\"displayName\":\"Y\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isUnauthorized());

        CopyOnWriteArrayList<String> published = new CopyOnWriteArrayList<>();
        MqttClient client = testClient("access-cmd-" + UUID.randomUUID());
        try {
            client.subscribe(
                    "site/INDORE/line/LINE-01/commands",
                    1,
                    (IMqttMessageListener) (topic, message) ->
                            published.add(new String(message.getPayload(), StandardCharsets.UTF_8)));

            MvcResult accepted = mockMvc.perform(post("/api/commands")
                            .header(BootstrapActorResolver.ACTOR_ID_HEADER, "op@plant")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "siteId": "INDORE",
                                      "lineId": "LINE-01",
                                      "tagCode": "VALVE_101_POS",
                                      "targetValue": 42.0,
                                      "requestedBy": "spoofed@factory.com",
                                      "reason": "operator write"
                                    }
                                    """))
                    .andExpect(status().isAccepted())
                    .andReturn();

            String commandId = objectMapper
                    .readTree(accepted.getResponse().getContentAsString())
                    .get("commandId")
                    .asText();
            assertThat(auditLogRepository.findByCommandId(UUID.fromString(commandId)).orElseThrow().getRequestedBy())
                    .isEqualTo("op@plant");

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published).isNotEmpty());
            JsonNode mqtt = objectMapper.readTree(published.getFirst());
            assertThat(mqtt.get("issuedBy").asText()).isEqualTo("op@plant");
            assertThat(mqtt.toString()).doesNotContain(TOKEN);
            assertThat(mqtt.toString()).doesNotContain("spoofed@factory.com");

            MvcResult supervisorAccepted = mockMvc.perform(post("/api/commands")
                            .header(BootstrapActorResolver.ACTOR_ID_HEADER, "sup@plant")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "siteId": "INDORE",
                                      "lineId": "LINE-01",
                                      "tagCode": "VALVE_101_POS",
                                      "targetValue": 43.0,
                                      "requestedBy": "spoofed@factory.com",
                                      "reason": "supervisor write"
                                    }
                                    """))
                    .andExpect(status().isAccepted())
                    .andReturn();
            String supervisorCommandId = objectMapper
                    .readTree(supervisorAccepted.getResponse().getContentAsString())
                    .get("commandId")
                    .asText();
            assertThat(auditLogRepository
                            .findByCommandId(UUID.fromString(supervisorCommandId))
                            .orElseThrow()
                            .getRequestedBy())
                    .isEqualTo("sup@plant");
        } finally {
            closeQuietly(client);
        }

        long beforeReject = auditLogRepository.count();
        mockMvc.perform(post("/api/commands")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "op@plant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "siteId": "INDORE",
                                  "lineId": "LINE-01",
                                  "tagCode": "VALVE_101_POS",
                                  "targetValue": 150.0,
                                  "requestedBy": "spoofed@factory.com",
                                  "reason": "out of range"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TARGET_OUT_OF_RANGE"));
        assertThat(auditLogRepository.count()).isEqualTo(beforeReject + 1);
        assertThat(auditLogRepository.findAll().stream()
                        .filter(row -> "150.0".equals(row.getRequestedValue()))
                        .findFirst()
                        .orElseThrow()
                        .getRequestedBy())
                .isEqualTo("op@plant");

        mockMvc.perform(post("/api/commands")
                        .header(BootstrapActorResolver.ACTOR_ID_HEADER, "viewer@factory.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "siteId": "INDORE",
                                  "lineId": "LINE-01",
                                  "tagCode": "VALVE_101_POS",
                                  "targetValue": 10.0,
                                  "reason": "viewer write"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/commands")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "siteId": "INDORE",
                                  "lineId": "LINE-01",
                                  "tagCode": "VALVE_101_POS",
                                  "targetValue": 10.0,
                                  "reason": "admin write"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/registry/sites")).andExpect(status().isOk());
        mockMvc.perform(get("/api/commands/audit")).andExpect(status().isOk());

        mockMvc.perform(get("/api/registry/sites").header(BootstrapActorResolver.SETUP_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/registry/sites").header(BootstrapActorResolver.ACTOR_ID_HEADER, "missing@x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/registry/sites").header(BootstrapActorResolver.ACTOR_ID_HEADER, "viewer@factory.com"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/registry/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"PUNE2\",\"name\":\"Pune 2\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult stream = mockMvc.perform(get("/api/stream/telemetry"))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(stream.getResponse().getStatus()).isLessThan(400);
        mockMvc.perform(get("/api/stream/telemetry").header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN))
                .andExpect(request().asyncStarted());
        mockMvc.perform(post("/api/registry/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"PUNE3\",\"name\":\"Pune 3\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult ingest = mockMvc.perform(post("/api/telemetry/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "siteId":"INDORE",
                                  "lineId":"LINE-01",
                                  "timestamp":"2026-11-19T12:00:00Z",
                                  "readings":[]
                                }
                                """))
                .andReturn();
        assertThat(ingest.getResponse().getStatus()).isNotEqualTo(401);

        mockMvc.perform(post("/api/does-not-exist")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/commands")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/registry/not-a-resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/registry/not-a-resource")
                        .header(BootstrapActorResolver.SETUP_TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private void seedActor(String actorId, String displayName, ActorRole role) {
        actorRepository.save(new ActorEntity(actorId, displayName, role));
    }

    private MqttClient testClient(String clientId) throws Exception {
        MqttClient client = new MqttClient(
                "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883), clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        client.connect(options);
        return client;
    }

    private static void closeQuietly(MqttClient client) {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (Exception ignored) {
            // test cleanup
        }
    }
}
