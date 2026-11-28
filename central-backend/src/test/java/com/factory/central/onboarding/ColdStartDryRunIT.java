package com.factory.central.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.central.access.AuthTestSupport;
import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.central.registry.LineRepository;
import com.factory.central.registry.SiteRepository;
import com.factory.central.registry.TagMappingRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
@Tag("SF-M36")
class ColdStartDryRunIT {

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
        registry.add("app.mqtt.client-id", () -> "central-cold-start-it-" + UUID.randomUUID());
        AuthTestSupport.registerBootstrapAdmin(registry);
        AuthTestSupport.registerBootstrapEdgeTokens(registry);
        registry.add("app.registry.seed-enabled", () -> "false");
    }

    @Autowired
    private MockMvc springMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private LineRepository lineRepository;

    @Autowired
    private TagMappingRepository tagMappingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Cookie adminCookie;

    @BeforeEach
    void setUpMockMvc() throws Exception {
        adminCookie = AuthTestSupport.adminSession(springMvc);
        mockMvc = AuthTestSupport.secureMvc(webApplicationContext);
    }

    @Test
    void emptyDatabaseToActiveEdgeManifest() throws Exception {
        assertThat(siteRepository.count()).isZero();
        assertThat(lineRepository.count()).isZero();
        assertThat(tagMappingRepository.count()).isZero();

        mockMvc.perform(get("/api/edge/SITE-COLD/config")).andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/registry/sites")
                                .cookie(adminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"siteCode":"SITE-COLD","name":"Cold Start Plant","timezone":"Asia/Kolkata","lifecycle":"DRAFT"}
                                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/registry/lines")
                                .cookie(adminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"siteCode":"SITE-COLD","lineCode":"REACTOR-01","name":"Reactor Line 1","lifecycle":"DRAFT"}
                                        """))
                .andExpect(status().isCreated());

        MvcResult template =
                mockMvc.perform(get("/api/onboarding/templates/reactor")
                                .cookie(adminCookie)
                                .param("format", "csv")
                                .param("siteId", "SITE-COLD")
                                .param("lineId", "REACTOR-01")
                                .param("endpointUrl", "opc.tcp://localhost:53530/OPCUA/SimulationServer")
                                .param("download", "false"))
                        .andExpect(status().isOk())
                        .andReturn();

        mockMvc.perform(post("/api/registry/tags/batch")
                        .cookie(adminCookie)
                        .contentType(MediaType.valueOf("text/csv"))
                        .content(template.getResponse().getContentAsString(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(5))
                .andExpect(jsonPath("$.failed").value(0));

        mockMvc.perform(put("/api/registry/sites/SITE-COLD")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"Cold Start Plant","timezone":"Asia/Kolkata","lifecycle":"ACTIVE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/registry/lines/SITE-COLD/REACTOR-01")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reactor Line 1\",\"lifecycle\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        jdbcTemplate.update(
                "UPDATE tag_mappings SET lifecycle = 'ACTIVE' WHERE site_id = ? AND line_id = ?",
                "SITE-COLD",
                "REACTOR-01");

        MvcResult credential = mockMvc.perform(
                        post("/api/access/edge-credentials")
                                .cookie(adminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"siteId":"SITE-COLD","label":"cold-start"}
                                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper
                .readTree(credential.getResponse().getContentAsString())
                .get("token")
                .asText();

        mockMvc.perform(get("/api/edge/SITE-COLD/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteId").value("SITE-COLD"))
                .andExpect(jsonPath("$.lines[0].tags.length()").value(5));
    }
}