package com.factory.central.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.central.access.AccessPolicy;
import com.factory.central.access.BootstrapActorResolver;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class OnboardingChecklistApiIT {

    private static final Set<String> CATEGORIES = Set.of(
            "OPC_UA_SECURITY",
            "MQTT_BROKER_SECURITY",
            "HEARTBEAT_AND_STALENESS",
            "TIMESCALEDB_RETENTION",
            "EDGE_PROVISIONING");

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
        registry.add("app.mqtt.client-id", () -> "central-onboarding-checklist-it-" + UUID.randomUUID());
        registry.add("app.bootstrap.token", () -> "it-bootstrap-token");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .defaultRequest(get("/").header(BootstrapActorResolver.SETUP_TOKEN_HEADER, "it-bootstrap-token"))
                .build();
    }

    @Test
    void checklistCoversFiveDomainsWithoutRuntimeLeak() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/onboarding/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("runtimeContext");
        assertThat(body).doesNotContain("probeEnabled");
        assertThat(body).doesNotContain("defaultEdgeBaseUrl");
        assertThat(body).doesNotContain("EDGE_BASE_URL");

        for (String category : CATEGORIES) {
            assertThat(body).contains(category);
        }
    }

    @Test
    void checklistCategoryFilterAndInvalidCategory() throws Exception {
        mockMvc.perform(get("/api/onboarding/checklist").param("category", "OPC_UA_SECURITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].category").value("OPC_UA_SECURITY"));

        mockMvc.perform(get("/api/onboarding/checklist").param("category", "NOT_A_DOMAIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OnboardingErrorCode.INVALID_PARAMETER));
    }

    @Test
    void openOnboardingRoutesEnforceInvalidActorHeaders() throws Exception {
        mockMvc.perform(get("/api/onboarding/templates")).andExpect(status().isOk());
        mockMvc.perform(get("/api/onboarding/templates/reactor")).andExpect(status().isOk());
        mockMvc.perform(get("/api/onboarding/checklist")).andExpect(status().isOk());

        MockMvc anonymous =
                MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        anonymous
                .perform(get("/api/onboarding/templates").header(BootstrapActorResolver.SETUP_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value(AccessPolicy.UNAUTHENTICATED_MESSAGE));

        anonymous
                .perform(get("/api/onboarding/checklist").header(BootstrapActorResolver.ACTOR_ID_HEADER, "missing@x"))
                .andExpect(status().isUnauthorized());
    }
}