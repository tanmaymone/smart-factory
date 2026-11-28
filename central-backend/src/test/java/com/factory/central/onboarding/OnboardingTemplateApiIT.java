package com.factory.central.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.central.access.BootstrapActorResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("SF-M36")
class OnboardingTemplateApiIT {

    private static final String CSV_HEADER =
            "siteId,lineId,tagCode,description,protocol,endpointUrl,externalAddress,dataType,unit,writable,minValue,maxValue,deadband,warningLow,warningHigh,criticalLow,criticalHigh,interlockRuleKey,lifecycle";
    private static final Pattern NS_NUMERIC = Pattern.compile("^ns=[1-9]");
    private static final Map<String, Integer> TAG_COUNTS = Map.of("reactor", 5, "utilities", 6, "vessel", 6);

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
        registry.add("app.mqtt.client-id", () -> "central-onboarding-template-it-" + UUID.randomUUID());
        registry.add("app.bootstrap.token", () -> "it-bootstrap-token");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .defaultRequest(get("/").header(BootstrapActorResolver.SETUP_TOKEN_HEADER, "it-bootstrap-token"))
                .build();
    }

    @Test
    void catalogListsThreeTemplatesWithCounts() throws Exception {
        mockMvc.perform(get("/api/onboarding/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.templates.length()").value(3))
                .andExpect(jsonPath("$.templates[?(@.templateKey=='reactor')].tagCount").value(5))
                .andExpect(jsonPath("$.templates[?(@.templateKey=='utilities')].tagCount").value(6))
                .andExpect(jsonPath("$.templates[?(@.templateKey=='vessel')].tagCount").value(6));
    }

    @Test
    void downloadCsvAndJsonRoundTripThroughBatchImport() throws Exception {
        seedSite("SF-M36");
        for (String key : List.of("reactor", "utilities", "vessel")) {
            String lineId = "LINE-" + key.toUpperCase();
            seedLine("SF-M36", lineId);
            int tagCount = TAG_COUNTS.get(key);
            MvcResult csv =
                    mockMvc.perform(get("/api/onboarding/templates/" + key)
                                    .param("format", "csv")
                                    .param("siteId", "SF-M36")
                                    .param("lineId", lineId)
                                    .param(
                                            "endpointUrl",
                                            "opc.tcp://localhost:53530/OPCUA/SimulationServer")
                                    .param("download", "true"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Content-Type", "text/csv;charset=utf-8"))
                            .andExpect(header().string("Content-Disposition", "attachment; filename=\"smart-factory-" + key + "-tags.csv\""))
                            .andReturn();
            String csvBody = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(csvBody.split("\r?\n", 2)[0]).isEqualTo(CSV_HEADER);
            assertCsvSemantics(csvBody);

            assertBatchPost(csvBody, MediaType.valueOf("text/csv"), tagCount, true);
            assertBatchPost(csvBody, MediaType.valueOf("text/csv"), tagCount, false);

            String jsonLine = lineId + "-JSON";
            seedLine("SF-M36", jsonLine);
            MvcResult json =
                    mockMvc.perform(get("/api/onboarding/templates/" + key)
                                    .param("format", "json")
                                    .param("siteId", "SF-M36")
                                    .param("lineId", jsonLine)
                                    .param(
                                            "endpointUrl",
                                            "opc.tcp://localhost:53530/OPCUA/SimulationServer"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Content-Disposition", "attachment; filename=\"smart-factory-" + key + "-tags.json\""))
                            .andReturn();
            JsonNode root = objectMapper.readTree(json.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(root.path("tags")).hasSize(tagCount);
            String batchJson = objectMapper.writeValueAsString(Map.of("tags", root.path("tags")));
            assertBatchPost(batchJson, MediaType.APPLICATION_JSON, tagCount, true);
            assertBatchPost(batchJson, MediaType.APPLICATION_JSON, tagCount, false);
        }
    }

    @Test
    void negativeTemplateRequests() throws Exception {
        mockMvc.perform(get("/api/onboarding/templates/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(OnboardingErrorCode.TEMPLATE_NOT_FOUND));

        mockMvc.perform(get("/api/onboarding/templates/reactor").param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OnboardingErrorCode.UNSUPPORTED_TEMPLATE_FORMAT));

        mockMvc.perform(get("/api/onboarding/templates/reactor").param("siteId", "bad id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OnboardingErrorCode.INVALID_PARAMETER));
    }

    private void seedSite(String siteId) throws Exception {
        mockMvc.perform(
                        post("/api/registry/sites")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"siteCode":"%s","name":"SF M36","timezone":"Asia/Kolkata","lifecycle":"DRAFT"}
                                        """
                                                .formatted(siteId)))
                .andExpect(status().isCreated());
    }

    private void seedLine(String siteId, String lineId) throws Exception {
        mockMvc.perform(
                        post("/api/registry/lines")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"siteCode":"%s","lineCode":"%s","name":"Line","lifecycle":"DRAFT"}
                                        """
                                                .formatted(siteId, lineId)))
                .andExpect(status().isCreated());
    }

    private void assertBatchPost(String body, MediaType type, int tagCount, boolean firstImport) throws Exception {
        var builder = post("/api/registry/tags/batch").contentType(type).content(body);
        var result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(0))
                .andReturn();
        if (firstImport) {
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("created").asInt())
                    .isEqualTo(tagCount);
        } else {
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("updated").asInt())
                    .isEqualTo(tagCount);
        }
    }

    private static void assertCsvSemantics(String csvBody) {
        for (String line : csvBody.split("\r?\n")) {
            if (line.isBlank() || line.startsWith("siteId,")) {
                continue;
            }
            assertThat(line).contains("OPC_UA");
            assertThat(line).contains("DRAFT");
            assertThat(NS_NUMERIC.matcher(line).find()).isFalse();
            assertThat(line).contains("nsu=");
        }
    }
}