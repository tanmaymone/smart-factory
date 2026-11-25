package com.factory.edge.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The single expected-value list for 422 status, code, message, and ordered fieldErrors. Central's
 * suite loads the same file, which is what keeps the two duplicated validators on one output. Gate
 * order lives in {@link OnboardingProbeControllerTest} and does not repeat these messages.
 */
@Tag("EDGE-PROBE")
@WebMvcTest(
        controllers = OnboardingProbeController.class,
        properties = "app.onboarding.probe-enabled=true")
@Import(OnboardingProbeMapperConfig.class)
class OnboardingProbeValidationContractTest {

    private static final String FIXTURE = "contracts/onboarding-probe-validation.json";
    private static final ObjectMapper FIXTURE_MAPPER = new ObjectMapper();

    @Autowired private MockMvc mockMvc;

    @MockitoBean private OnboardingProbeService probeService;

    static List<Arguments> rows() throws Exception {
        Path fixture = locateFixture();
        List<Arguments> rows = new ArrayList<>();
        FIXTURE_MAPPER
                .readTree(Files.readAllBytes(fixture))
                .forEach(row -> rows.add(Arguments.of(row.get("name").asText(), row)));
        assertThat(rows).isNotEmpty();
        return rows;
    }

    /** Walks parent directories from the working directory and fails if the walk misses the file. */
    private static Path locateFixture() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(FIXTURE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        return fail("No parent of %s contains %s".formatted(Path.of("").toAbsolutePath(), FIXTURE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void fixtureRowIsRejectedWithoutTakingTheLock(String name, JsonNode row) throws Exception {
        MvcResult result =
                mockMvc
                        .perform(
                                post("/api/onboarding/probe")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(FIXTURE_MAPPER.writeValueAsBytes(row.get("body"))))
                        .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(row.get("status").asInt());
        JsonNode body = FIXTURE_MAPPER.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.get("code").asText()).isEqualTo(row.get("code").asText());
        assertThat(body.get("message").asText()).isEqualTo(row.get("message").asText());
        assertThat(body.get("fieldErrors")).isEqualTo(row.get("fieldErrors"));

        // A 422 never calls tryLock, never opens a socket, and never starts the 8s deadline.
        verifyNoInteractions(probeService);
    }
}
