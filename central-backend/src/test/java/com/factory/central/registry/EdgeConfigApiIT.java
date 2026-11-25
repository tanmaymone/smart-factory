package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.central.access.BootstrapActorResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Tag("SF-REGISTRY")
class EdgeConfigApiIT {

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
        registry.add("app.bootstrap.token", () -> "it-bootstrap-token");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SiteRepository siteRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .defaultRequest(get("/").header(BootstrapActorResolver.SETUP_TOKEN_HEADER, "it-bootstrap-token"))
                .build();
    }

    @Test
    void seededIndoreManifestContainsActiveOpcTagsAndOmitsEmptyLines() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/edge/INDORE/config"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.siteId").value("INDORE"))
                .andExpect(jsonPath("$.siteLifecycle").value("ACTIVE"))
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.contentHash").isString())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("version").asLong()).isGreaterThanOrEqualTo(1L);
        int tagCount = 0;
        for (JsonNode line : body.get("lines")) {
            assertThat(line.get("tags")).isNotEmpty();
            tagCount += line.get("tags").size();
            for (JsonNode tag : line.get("tags")) {
                assertThat(tag.get("protocol").asText()).isEqualTo("OPC_UA");
                assertThat(tag.has("description")).isFalse();
                assertThat(tag.has("unit")).isFalse();
            }
        }
        assertThat(tagCount).isEqualTo(4);
    }

    @Test
    void unknownSiteAndWrongCaseAndIllegalSiteId() throws Exception {
        mockMvc.perform(get("/api/edge/NO_SUCH/config")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"v1\""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.SITE_NOT_FOUND));

        mockMvc.perform(get("/api/edge/indore/config"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.SITE_NOT_FOUND));

        mockMvc.perform(get("/api/edge/BAD SITE!/config"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(RegistryErrorCode.INVALID_PARAMETER));
    }

    @Test
    void ifNoneMatchReturns304AndEmptyWhitespaceHeaderReturns200() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/edge/INDORE/config"))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        mockMvc.perform(get("/api/edge/INDORE/config").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));

        mockMvc.perform(get("/api/edge/INDORE/config").header(HttpHeaders.IF_NONE_MATCH, "W/\"v999\", " + etag))
                .andExpect(status().isNotModified());

        mockMvc.perform(get("/api/edge/INDORE/config").header(HttpHeaders.IF_NONE_MATCH, "   "))
                .andExpect(status().isOk());
    }

    @Test
    void demotedSiteReturnsEmptyLinesAndNameOnlyBumpKeepsHash() throws Exception {
        mockMvc.perform(post("/api/registry/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"siteCode":"NAGPUR","name":"Nagpur","timezone":"Asia/Kolkata","lifecycle":"ACTIVE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/registry/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"siteCode":"NAGPUR","lineCode":"LINE-01","name":"Line","lifecycle":"ACTIVE"}
                                """))
                .andExpect(status().isCreated());

        MvcResult before = mockMvc.perform(get("/api/edge/NAGPUR/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andReturn();
        JsonNode beforeBody = objectMapper.readTree(before.getResponse().getContentAsString());
        long versionBefore = beforeBody.get("version").asLong();
        String hashBefore = beforeBody.get("contentHash").asText();

        mockMvc.perform(put("/api/registry/sites/NAGPUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"Nagpur Plant","timezone":"Asia/Kolkata","lifecycle":"ACTIVE"}
                                """))
                .andExpect(status().isOk());

        MvcResult after = mockMvc.perform(get("/api/edge/NAGPUR/config"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterBody = objectMapper.readTree(after.getResponse().getContentAsString());
        assertThat(afterBody.get("version").asLong()).isGreaterThan(versionBefore);
        assertThat(afterBody.get("contentHash").asText()).isEqualTo(hashBefore);

        mockMvc.perform(put("/api/registry/sites/NAGPUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"Nagpur Plant","timezone":"Asia/Kolkata","lifecycle":"DRAFT"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/edge/NAGPUR/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteLifecycle").value("DRAFT"))
                .andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void missingRevisionRowDefaultsToVersionOne() {
        assertThat(siteRepository.findById("INDORE")).isPresent();
        jdbcTemplate.update("DELETE FROM site_config_revisions WHERE site_id = ?", "PUNE");
        try {
            mockMvc.perform(get("/api/edge/PUNE/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.lines").isArray());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        } finally {
            jdbcTemplate.update(
                    """
                    INSERT INTO site_config_revisions (site_id, version, updated_at)
                    VALUES ('PUNE', 1, now())
                    ON CONFLICT (site_id) DO NOTHING
                    """);
        }
    }

    @Test
    void anonymousGetAllowedUnknownActorRejected() throws Exception {
        MockMvc openMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        openMvc.perform(get("/api/edge/INDORE/config")).andExpect(status().isOk());

        openMvc.perform(get("/api/edge/INDORE/config").header(BootstrapActorResolver.ACTOR_ID_HEADER, "missing-actor"))
                .andExpect(status().isUnauthorized());
    }
}
