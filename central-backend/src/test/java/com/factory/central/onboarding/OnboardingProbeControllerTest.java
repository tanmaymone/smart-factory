package com.factory.central.onboarding;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.ServletInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Gate order: auth, flag, 413, 400, 422, forward. Every assertion here stacks a later failure on
 * the request so the earlier status is what proves the order. Messages for 422 rows live in
 * contracts/onboarding-probe-validation.json, not here.
 */
@Tag("EDGE-PROBE")
class OnboardingProbeControllerTest {

    private static final String PATH = "/api/onboarding/probe";
    private static final String VALID_BODY =
            "{\"endpointUrl\":\"opc.tcp://localhost:53530/OPCUA/SimulationServer\",\"securityMode\":\"None\"}";
    private static final byte[] OVERSIZED_INVALID_JSON = ("{" + "x".repeat(40_000)).getBytes(UTF_8);

    private StubEdgeServer edge;

    @BeforeEach
    void setUp() throws IOException {
        edge = StubEdgeServer.start();
    }

    @AfterEach
    void tearDown() {
        edge.close();
    }

    @Test
    void anonymousOversizedBodyIsUnauthenticatedBeforeTheFlag() throws Exception {
        mockMvc(false)
                .perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(OVERSIZED_INVALID_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(edge.requests()).isEmpty();
    }

    @Test
    void nonAdminOversizedBodyIsForbiddenNotProbeDisabled() throws Exception {
        mockMvc(false)
                .perform(post(PATH)
                        .header(ProbeTestSupport.actorIdHeader(), ProbeTestSupport.OPERATOR_ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OVERSIZED_INVALID_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(edge.requests()).isEmpty();
    }

    @Test
    void flagOffIsProbeDisabledAndLeavesTheStreamUnread() throws Exception {
        mockMvc(false)
                .perform(requestRefusingStreamReads(40_000L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROBE_DISABLED"))
                .andExpect(jsonPath("$.message").value("OPC UA probe is disabled"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        assertThat(edge.requests()).isEmpty();
    }

    @Test
    void declaredLengthAboveCapIs413AndLeavesTheStreamUnread() throws Exception {
        mockMvc(true)
                .perform(requestRefusingStreamReads(32_769L))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("BODY_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("probe body exceeds 32KiB"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        assertThat(edge.requests()).isEmpty();
    }

    @Test
    void lengthlessStreamOfOneByteAboveCapIs413BeforeBinding() throws Exception {
        byte[] body = ("{" + "x".repeat(32_768)).getBytes(UTF_8);
        assertThat(body).hasSize(32_769);

        mockMvc(true)
                .perform(lengthlessRequest(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("BODY_TOO_LARGE"));

        assertThat(edge.requests()).isEmpty();
    }

    @Test
    void exactlyCapSizedBodyPassesTheSizeGateAndForwards() throws Exception {
        byte[] body = (VALID_BODY + " ".repeat(32_768 - VALID_BODY.length())).getBytes(UTF_8);
        assertThat(body).hasSize(32_768);

        mockMvc(true).perform(lengthlessRequest(body)).andExpect(status().isOk());

        assertThat(edge.requests()).hasSize(1);
        assertThat(edge.requests().get(0)).isEqualTo(body);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "null",
                "{",
                "{\"nodeIds\":\"foo\"}",
                "{\"endpointUrl\":\"opc.tcp://localhost:53530\",\"securityMode\":1}",
                "{\"endpointUrl\":\"opc.tcp://localhost:53530\",\"securityMode\":1.5}",
                "{\"endpointUrl\":\"opc.tcp://localhost:53530\",\"securityMode\":true}",
                "{\"endpointUrl\":123,\"securityMode\":\"None\"}",
                "{\"endpointUrl\":\"opc.tcp://localhost:53530\",\"securityMode\":\"None\",\"nodeIds\":[\"a\",5]}",
                "{\"endpointUrl\":[\"opc.tcp://localhost:53530\"],\"securityMode\":\"None\"}"
            })
    void unboundableBodyIsInvalidRequestBodyNot422(String body) throws Exception {
        mockMvc(true)
                .perform(post(PATH)
                        .header(ProbeTestSupport.onboardingAdminHeader(), "ONBOARDING_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("probe body is invalid"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());

        assertThat(edge.requests()).isEmpty();
    }

    @Test
    void semanticFailureIs422AndIsNotForwarded() throws Exception {
        mockMvc(true)
                .perform(post(PATH)
                        .header(ProbeTestSupport.onboardingAdminHeader(), "ONBOARDING_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpointUrl\":\"opc.tcp://\",\"securityMode\":\"none\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENDPOINT_URL_INVALID"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        assertThat(edge.requests()).isEmpty();
    }

    @Test
    void acceptedUrlShapesAreForwardedNot422() throws Exception {
        for (String endpointUrl : new String[] {
            "OPC.TCP://localhost:53530/OPCUA/SimulationServer",
            "opc.tcp://plc_1/OPCUA/SimulationServer",
            "opc.tcp://[::1]:4840/OPCUA/SimulationServer",
            "opc.tcp://[::ffff:192.0.2.1]:4840/OPCUA/SimulationServer",
            "opc.tcp://localhost:53530"
        }) {
            mockMvc(true)
                    .perform(post(PATH)
                            .header(ProbeTestSupport.onboardingAdminHeader(), "ONBOARDING_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"endpointUrl\":\"" + endpointUrl + "\",\"securityMode\":\"None\"}"))
                    .andExpect(status().isOk());
        }

        assertThat(edge.requests()).hasSize(5);
    }

    private MockMvc mockMvc(boolean probeEnabled) {
        OnboardingProbeProperties properties = ProbeTestSupport.properties(probeEnabled, edge.baseUrl());
        return ProbeTestSupport.mockMvc(properties, new OnboardingProbeForwarder(properties));
    }

    private static RequestBuilder requestRefusingStreamReads(long declaredLength) {
        return servletContext -> {
            MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "POST", PATH) {
                @Override
                public ServletInputStream getInputStream() {
                    throw new AssertionError("request body stream must not be read");
                }

                @Override
                public BufferedReader getReader() {
                    throw new AssertionError("request reader must not be used");
                }

                @Override
                public long getContentLengthLong() {
                    return declaredLength;
                }

                @Override
                public int getContentLength() {
                    return (int) declaredLength;
                }
            };
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.addHeader(ProbeTestSupport.onboardingAdminHeader(), "ONBOARDING_ADMIN");
            return request;
        };
    }

    /** Chunked or otherwise length-less: only the bounded read can enforce the cap. */
    private static RequestBuilder lengthlessRequest(byte[] content) {
        return servletContext -> {
            MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "POST", PATH) {
                @Override
                public long getContentLengthLong() {
                    return -1L;
                }

                @Override
                public int getContentLength() {
                    return -1;
                }
            };
            request.setContent(content);
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.addHeader(ProbeTestSupport.onboardingAdminHeader(), "ONBOARDING_ADMIN");
            return request;
        };
    }
}
