package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.central.access.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
@Tag("SF-M42")
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
        AuthTestSupport.registerBootstrapAdmin(registry);
        AuthTestSupport.registerBootstrapEdgeTokens(registry);
    }

    @Autowired
    private MockMvc springMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SiteRepository siteRepository;

    private MockMvc mockMvc;
    private Cookie adminCookie;

    @BeforeEach
    void setUpMockMvc() throws Exception {
        adminCookie = AuthTestSupport.adminSession(springMvc);
        mockMvc = AuthTestSupport.secureMvc(webApplicationContext);
    }

    @Test
    void seededIndoreManifestContainsActiveOpcTagsAndOmitsEmptyLines() throws Exception {
        MvcResult result = mockMvc.perform(edgeConfigGet("INDORE"))
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
        mockMvc.perform(edgeConfigGet("NO_SUCH").header(HttpHeaders.IF_NONE_MATCH, "\"v1\""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDGE_SITE_FORBIDDEN"));

        mockMvc.perform(get("/api/edge/indore/config").header(HttpHeaders.AUTHORIZATION, indoreBearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDGE_SITE_FORBIDDEN"));

        mockMvc.perform(get("/api/edge/BAD SITE!/config").header(HttpHeaders.AUTHORIZATION, indoreBearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDGE_SITE_FORBIDDEN"));
    }

    @Test
    void ifNoneMatchReturns304AndEmptyWhitespaceHeaderReturns200() throws Exception {
        MvcResult first = mockMvc.perform(edgeConfigGet("INDORE"))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        mockMvc.perform(edgeConfigGet("INDORE").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));

        mockMvc.perform(edgeConfigGet("INDORE").header(HttpHeaders.IF_NONE_MATCH, "W/\"v999\", " + etag))
                .andExpect(status().isNotModified());

        mockMvc.perform(edgeConfigGet("INDORE").header(HttpHeaders.IF_NONE_MATCH, "   "))
                .andExpect(status().isOk());
    }

    @Test
    void demotedSiteReturnsEmptyLinesAndNameOnlyBumpKeepsHash() throws Exception {
        mockMvc.perform(post("/api/registry/sites")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"siteCode":"NAGPUR","name":"Nagpur","timezone":"Asia/Kolkata","lifecycle":"ACTIVE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/registry/lines")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"siteCode":"NAGPUR","lineCode":"LINE-01","name":"Line","lifecycle":"ACTIVE"}
                                """))
                .andExpect(status().isCreated());

        String nagpurToken = mintEdgeToken("NAGPUR", "nagpur-edge");

        MvcResult before = mockMvc.perform(edgeConfigGet("NAGPUR", nagpurToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andReturn();
        JsonNode beforeBody = objectMapper.readTree(before.getResponse().getContentAsString());
        long versionBefore = beforeBody.get("version").asLong();
        String hashBefore = beforeBody.get("contentHash").asText();

        mockMvc.perform(put("/api/registry/sites/NAGPUR")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"Nagpur Plant","timezone":"Asia/Kolkata","lifecycle":"ACTIVE"}
                                """))
                .andExpect(status().isOk());

        MvcResult after = mockMvc.perform(edgeConfigGet("NAGPUR", nagpurToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterBody = objectMapper.readTree(after.getResponse().getContentAsString());
        assertThat(afterBody.get("version").asLong()).isGreaterThan(versionBefore);
        assertThat(afterBody.get("contentHash").asText()).isEqualTo(hashBefore);

        mockMvc.perform(put("/api/registry/sites/NAGPUR")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"Nagpur Plant","timezone":"Asia/Kolkata","lifecycle":"DRAFT"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(edgeConfigGet("NAGPUR", nagpurToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteLifecycle").value("DRAFT"))
                .andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void missingRevisionRowDefaultsToVersionOne() throws Exception {
        assertThat(siteRepository.findById("INDORE")).isPresent();
        jdbcTemplate.update("DELETE FROM site_config_revisions WHERE site_id = ?", "PUNE");
        try {
            mockMvc.perform(edgeConfigGet("PUNE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.lines").isArray());
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
    void edgeTokenAuthRejectsAnonymousHumanAndCrossSite() throws Exception {
        MockMvc openMvc = AuthTestSupport.secureMvc(webApplicationContext);

        openMvc.perform(get("/api/edge/INDORE/config")).andExpect(status().isUnauthorized());
        openMvc.perform(get("/api/edge/INDORE/config").header(HttpHeaders.AUTHORIZATION, "Bearer wrong"))
                .andExpect(status().isUnauthorized());

        openMvc.perform(get("/api/edge/INDORE/config").cookie(adminCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        openMvc.perform(get("/api/edge/PUNE/config").header(HttpHeaders.AUTHORIZATION, indoreBearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDGE_SITE_FORBIDDEN"));

        openMvc.perform(get("/api/edge/INDORE/config").header(AuthTestSupport.LEGACY_ACTOR_HEADER, "missing-actor"))
                .andExpect(status().isUnauthorized());

        openMvc.perform(post("/api/commands")
                        .header(HttpHeaders.AUTHORIZATION, indoreBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        openMvc.perform(post("/api/registry/sites")
                        .header(HttpHeaders.AUTHORIZATION, indoreBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        JsonNode created = objectMapper.readTree(mintEdgeCredentialBody("INDORE", "rotate-me"));
        String rawToken = created.get("token").asText();
        String credentialId = created.get("id").asText();

        openMvc.perform(get("/api/edge/INDORE/config").header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/access/edge-credentials/" + credentialId).cookie(adminCookie))
                .andExpect(status().isNoContent());

        openMvc.perform(get("/api/edge/INDORE/config").header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken))
                .andExpect(status().isUnauthorized());
    }

    private String mintEdgeCredentialBody(String siteId, String label) throws Exception {
        return mockMvc.perform(post("/api/access/edge-credentials")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"siteId":"%s","label":"%s"}
                                """
                                        .formatted(siteId, label)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String mintEdgeToken(String siteId, String label) throws Exception {
        return objectMapper.readTree(mintEdgeCredentialBody(siteId, label)).get("token").asText();
    }

    private static String indoreBearer() {
        return "Bearer " + AuthTestSupport.INDORE_EDGE_TOKEN;
    }

    private static String edgeBearerFor(String siteId) {
        if ("PUNE".equals(siteId)) {
            return "Bearer " + AuthTestSupport.PUNE_EDGE_TOKEN;
        }
        return indoreBearer();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder edgeConfigGet(String siteId) {
        return get("/api/edge/" + siteId + "/config")
                .header(HttpHeaders.AUTHORIZATION, edgeBearerFor(siteId));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder edgeConfigGet(
            String siteId, String rawToken) {
        return get("/api/edge/" + siteId + "/config").header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);
    }
}