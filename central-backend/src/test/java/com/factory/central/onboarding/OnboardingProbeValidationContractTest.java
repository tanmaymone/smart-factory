package com.factory.central.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The 422 rules are duplicated in Central and the edge. contracts/onboarding-probe-validation.json
 * is the only expected-value list for status, code, message, and ordered fieldErrors, and both
 * suites load it so the two copies cannot drift.
 */
@Tag("EDGE-PROBE")
class OnboardingProbeValidationContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StubEdgeServer edge;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        edge = StubEdgeServer.start();
        OnboardingProbeProperties properties = ProbeTestSupport.properties(true, edge.baseUrl());
        mockMvc = ProbeTestSupport.mockMvc(properties, new OnboardingProbeForwarder(properties));
    }

    @AfterEach
    void tearDown() {
        edge.close();
    }

    @Test
    void fixtureIsPresentAndNotEmpty() throws IOException {
        assertThat(rowsFromFixture()).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void returnsFixtureStatusCodeMessageAndFieldErrors(String name, JsonNode row) throws Exception {
        byte[] body = MAPPER.writeValueAsBytes(row.get("body"));

        MvcResult result = mockMvc.perform(post("/api/onboarding/probe")
                        .header(ProbeTestSupport.onboardingAdminHeader(), "ONBOARDING_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(row.get("status").asInt());
        JsonNode response = MAPPER.readTree(result.getResponse().getContentAsByteArray());
        assertThat(response.get("code").asText()).isEqualTo(row.get("code").asText());
        assertThat(response.get("message").asText()).isEqualTo(row.get("message").asText());
        assertThat(response.get("fieldErrors")).isEqualTo(row.get("fieldErrors"));
        assertThat(edge.requests()).isEmpty();
    }

    static Stream<Arguments> rows() throws IOException {
        List<Arguments> arguments = new ArrayList<>();
        for (JsonNode row : rowsFromFixture()) {
            arguments.add(Arguments.of(row.get("name").asText(), row));
        }
        return arguments.stream();
    }

    private static JsonNode rowsFromFixture() throws IOException {
        return MAPPER.readTree(ProbeTestSupport.validationFixture());
    }
}
