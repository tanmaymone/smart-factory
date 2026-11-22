package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.LatestTagCache;
import com.factory.central.cache.LatestTagState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.IMqttClient;
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
@Tag("SF-M1.3")
class RegistryApiIT {

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegistrySeedRunner registrySeedRunner;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private LineRepository lineRepository;

    @Autowired
    private TagMappingRepository tagMappingRepository;

    @Autowired
    private LatestTagCache latestTagCache;

    @Autowired
    private IMqttClient mqttClient;

    @Test
    void listsTagsWithOptionalFiltersAndFullValve201Fields() throws Exception {
        mockMvc.perform(get("/api/registry/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));

        mockMvc.perform(get("/api/registry/tags").param("siteId", "INDORE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        mockMvc.perform(get("/api/registry/tags")
                        .param("siteId", "INDORE")
                        .param("lineId", "LINE-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/registry/tags")
                        .param("siteId", "INDORE")
                        .param("lineId", "LINE-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tagCode").value("VALVE_201_POS"))
                .andExpect(jsonPath("$[0].unit").value("%"))
                .andExpect(jsonPath("$[0].writable").value(true))
                .andExpect(jsonPath("$[0].minValue").value(0.0))
                .andExpect(jsonPath("$[0].maxValue").value(100.0))
                .andExpect(jsonPath("$[0].deadband").value(0.5))
                .andExpect(jsonPath("$[0].warningLow").value(nullValue()))
                .andExpect(jsonPath("$[0].warningHigh").value(nullValue()))
                .andExpect(jsonPath("$[0].criticalLow").value(nullValue()))
                .andExpect(jsonPath("$[0].criticalHigh").value(nullValue()));

        mockMvc.perform(get("/api/registry/tags").param("protocol", "OPC_UA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));

        mockMvc.perform(get("/api/registry/tags").param("writable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(get("/api/registry/tags").param("siteId", "NO_SUCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createAndUpdateRoundTripThresholds() throws Exception {
        TagMappingRequest createRequest =
                sampleRequest("INDORE", "LINE-01", "CUSTOM_TEMP_C", 30.0, 70.0, 20.0, 80.0);

        MvcResult created = mockMvc.perform(post("/api/registry/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/registry/tags/")))
                .andExpect(jsonPath("$.tagCode").value("CUSTOM_TEMP_C"))
                .andExpect(jsonPath("$.warningLow").value(30.0))
                .andExpect(jsonPath("$.warningHigh").value(70.0))
                .andExpect(jsonPath("$.criticalLow").value(20.0))
                .andExpect(jsonPath("$.criticalHigh").value(80.0))
                .andReturn();

        UUID id = readId(created);

        TagMappingRequest updateRequest =
                sampleRequest("INDORE", "LINE-01", "CUSTOM_TEMP_C", 25.0, 65.0, 15.0, 85.0);

        mockMvc.perform(put("/api/registry/tags/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.warningLow").value(25.0))
                .andExpect(jsonPath("$.warningHigh").value(65.0))
                .andExpect(jsonPath("$.criticalLow").value(15.0))
                .andExpect(jsonPath("$.criticalHigh").value(85.0));

        tagMappingRepository.deleteById(id);
    }

    @Test
    void deleteRemovesTagAndLatestCacheThenMissingIdIsNotFound() throws Exception {
        TagMappingRequest request = sampleRequest("INDORE", "LINE-01", "DELETE_ME_TAG", null, null, null, null);
        MvcResult created = mockMvc.perform(post("/api/registry/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = readId(created);

        latestTagCache.put(LatestTagState.initial(
                "INDORE",
                "LINE-01",
                "DELETE_ME_TAG",
                12.5,
                "GOOD",
                Instant.parse("2026-11-21T00:00:00Z"),
                AlarmSeverity.WARNING,
                AlarmDirection.HIGH));

        mockMvc.perform(delete("/api/registry/tags/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(tagMappingRepository.findById(id)).isEmpty();
        assertThat(latestTagCache.get("INDORE", "LINE-01", "DELETE_ME_TAG")).isEmpty();
        assertThat(latestTagCache.findAlarms("INDORE", "LINE-01"))
                .noneMatch(state -> "DELETE_ME_TAG".equals(state.tagCode()));

        mockMvc.perform(get("/api/registry/tags").param("siteId", "INDORE").param("lineId", "LINE-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tagCode == 'DELETE_ME_TAG')]").isEmpty());

        mockMvc.perform(delete("/api/registry/tags/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.TAG_NOT_FOUND));
    }

    @Test
    void deleteInvalidIdReturnsInvalidParameter() throws Exception {
        mockMvc.perform(delete("/api/registry/tags/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.INVALID_PARAMETER))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    void rejectsPartialInvertedThresholdsWithInvalidBounds() throws Exception {
        TagMappingRequest request = sampleRequest("INDORE", "LINE-01", "BAD_THRESH", 40.0, null, 50.0, null);

        mockMvc.perform(post("/api/registry/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.INVALID_BOUNDS))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void postUnknownLineReturnsLineNotFound() throws Exception {
        TagMappingRequest request = sampleRequest("INDORE", "NO_LINE", "X", null, null, null, null);

        mockMvc.perform(post("/api/registry/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.LINE_NOT_FOUND));
    }

    @Test
    void putMissingIdReturnsTagNotFoundBeforeLineLookup() throws Exception {
        UUID missing = UUID.randomUUID();
        TagMappingRequest request = sampleRequest("INDORE", "NO_LINE", "X", null, null, null, null);

        mockMvc.perform(put("/api/registry/tags/" + missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.TAG_NOT_FOUND));
    }

    @Test
    void putRejectsSiteOrLineChangeAndAllowsTagCodeRename() throws Exception {
        TagMapping existing = tagMappingRepository
                .findBySiteIdAndLineIdAndTagCode("INDORE", "LINE-02", "VALVE_201_POS")
                .orElseThrow();

        TagMappingRequest moved = sampleRequest("PUNE", "LINE-01", "VALVE_201_POS", null, null, null, null);
        mockMvc.perform(put("/api/registry/tags/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moved)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.IDENTITY_IMMUTABLE));

        TagMappingRequest renamed = fromEntity(existing, "VALVE_201_RENAMED");
        mockMvc.perform(put("/api/registry/tags/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagCode").value("VALVE_201_RENAMED"));

        TagMappingRequest restore = fromEntity(existing, "VALVE_201_POS");
        mockMvc.perform(put("/api/registry/tags/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restore)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagCode").value("VALVE_201_POS"));

        TagMapping reactor = tagMappingRepository
                .findBySiteIdAndLineIdAndTagCode("INDORE", "LINE-01", "REACTOR_TEMP_C")
                .orElseThrow();
        TagMappingRequest ontoValve = fromEntity(reactor, "VALVE_101_POS");
        mockMvc.perform(put("/api/registry/tags/" + reactor.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ontoValve)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.TAG_CODE_EXISTS));
    }

    @Test
    void postDuplicateNaturalKeyReturnsConflict() throws Exception {
        TagMappingRequest request = sampleRequest("INDORE", "LINE-01", "VALVE_101_POS", null, null, null, null);

        mockMvc.perform(post("/api/registry/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.TAG_CODE_EXISTS));
    }

    @Test
    void invalidProtocolQueryReturnsInvalidParameter() throws Exception {
        mockMvc.perform(get("/api/registry/tags").param("protocol", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.INVALID_PARAMETER))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("protocol"));
    }

    @Test
    void seedIsIdempotentOnSecondRun() {
        registrySeedRunner.run();

        assertThat(siteRepository.count()).isEqualTo(2);
        assertThat(lineRepository.count()).isEqualTo(3);
        assertThat(tagMappingRepository.count()).isEqualTo(7);

        TagMapping reactor = tagMappingRepository
                .findBySiteIdAndLineIdAndTagCode("INDORE", "LINE-01", "REACTOR_TEMP_C")
                .orElseThrow();
        assertThat(reactor.getWarningLow()).isEqualTo(30.0);
        assertThat(reactor.getWarningHigh()).isEqualTo(70.0);
        assertThat(reactor.getCriticalLow()).isEqualTo(20.0);
        assertThat(reactor.getCriticalHigh()).isEqualTo(80.0);
    }

    @Test
    void mqttClientConnectsToBroker() {
        assertThat(mqttClient.isConnected()).isTrue();
    }

    private UUID readId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(root.get("id").asText());
    }

    private static TagMappingRequest fromEntity(TagMapping tag, String tagCode) {
        return new TagMappingRequest(
                tag.getSiteId(),
                tag.getLineId(),
                tagCode,
                tag.getDescription(),
                tag.getProtocol(),
                tag.getEndpointUrl(),
                tag.getExternalAddress(),
                tag.getDataType(),
                tag.getUnit(),
                tag.isWritable(),
                tag.getMinValue(),
                tag.getMaxValue(),
                tag.getDeadband(),
                tag.getWarningLow(),
                tag.getWarningHigh(),
                tag.getCriticalLow(),
                tag.getCriticalHigh(),
                tag.getInterlockRuleKey());
    }

    private static TagMappingRequest sampleRequest(
            String siteId,
            String lineId,
            String tagCode,
            Double warningLow,
            Double warningHigh,
            Double criticalLow,
            Double criticalHigh) {
        return new TagMappingRequest(
                siteId,
                lineId,
                tagCode,
                "Test tag " + tagCode,
                Protocol.OPC_UA,
                "opc.tcp://localhost:53530/OPCUA/SimulationServer",
                "ns=3;i=9001",
                TagDataType.FLOAT,
                "°C",
                false,
                null,
                null,
                0.2,
                warningLow,
                warningHigh,
                criticalLow,
                criticalHigh,
                null);
    }
}
