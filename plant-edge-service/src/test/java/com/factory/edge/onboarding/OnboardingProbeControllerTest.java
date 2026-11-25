package com.factory.edge.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gate order: a later gate does not run, so a request that would also fail a later check still
 * returns the earlier status. The expected 422 code, message, and fieldErrors live only in
 * {@link OnboardingProbeValidationContractTest}.
 */
@Tag("EDGE-PROBE")
@WebMvcTest(
        controllers = OnboardingProbeController.class,
        properties = "app.onboarding.probe-enabled=true")
@Import(OnboardingProbeMapperConfig.class)
class OnboardingProbeControllerTest {

    private static final String VALID_BODY =
            "{\"endpointUrl\":\"OPC.TCP://localhost:53530/OPCUA/SimulationServer\","
                    + "\"securityMode\":\"None\"}";

    @Autowired private MockMvc mockMvc;

    @Autowired private OnboardingProbeMapperConfig mapperConfig;

    @MockitoBean private OnboardingProbeService probeService;

    @Test
    void oversizedBodyThatIsAlsoInvalidJsonIs413AndNotBound() throws Exception {
        mockMvc
                .perform(
                        post("/api/onboarding/probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{".repeat(32_769)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("BODY_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("probe body exceeds 32KiB"));

        verifyNoInteractions(probeService);
    }

    @Test
    void exactlyThirtyTwoKibIsNotPayloadTooLarge() throws Exception {
        when(probeService.probe(any(), any())).thenReturn(Optional.of(okResponse()));
        String padded = VALID_BODY + " ".repeat(32_768 - VALID_BODY.length());
        assertThat(padded.getBytes(StandardCharsets.UTF_8)).hasSize(32_768);

        mockMvc
                .perform(
                        post("/api/onboarding/probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(padded))
                .andExpect(status().isOk());
    }

    @Test
    void scalarCoercionsAreFourHundredWithNoFieldErrors() throws Exception {
        for (String body :
                List.of(
                        "{\"endpointUrl\":\"opc.tcp://localhost:53530\",\"securityMode\":1}",
                        "{\"endpointUrl\":\"opc.tcp://localhost:53530\",\"securityMode\":1.5}",
                        "{\"endpointUrl\":\"opc.tcp://localhost:53530\",\"securityMode\":true}",
                        "{\"endpointUrl\":123,\"securityMode\":\"None\"}",
                        "{\"endpointUrl\":\"opc.tcp://localhost:53530\",\"securityMode\":\"None\","
                                + "\"nodeIds\":[\"a\",5]}",
                        "{\"nodeIds\":\"foo\"}",
                        "{\"endpointUrl\":",
                        "null",
                        "")) {
            mockMvc
                    .perform(
                            post("/api/onboarding/probe")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                    .andExpect(jsonPath("$.message").value("probe body is invalid"))
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                    // Not Spring Boot's default error document.
                    .andExpect(jsonPath("$.timestamp").doesNotExist())
                    .andExpect(jsonPath("$.path").doesNotExist());
        }

        verifyNoInteractions(probeService);
    }

    @Test
    void semanticFailureIs422AndNeverReachesTheLock() throws Exception {
        mockMvc
                .perform(
                        post("/api/onboarding/probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"endpointUrl\":\"opc.tcp://localhost:53530\","
                                                + "\"securityMode\":\"none\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SECURITY_MODE_UNSUPPORTED"));

        verifyNoInteractions(probeService);
    }

    @Test
    void lockHeldIs409WithNoWait() throws Exception {
        when(probeService.probe(any(), any())).thenReturn(Optional.empty());

        mockMvc
                .perform(
                        post("/api/onboarding/probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("PROBE_IN_PROGRESS"));
    }

    @Test
    void acceptedProbeEchoesTheSubmittedSpelling() throws Exception {
        when(probeService.probe(any(), any())).thenReturn(Optional.of(okResponse()));

        mockMvc
                .perform(
                        post("/api/onboarding/probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.endpointUrl")
                                .value("OPC.TCP://localhost:53530/OPCUA/SimulationServer"))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.error").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.tags").isArray());
    }

    @Test
    void flagOffWinsOverEverythingAndDoesNotReadTheStream() throws Exception {
        OnboardingProbeProperties disabled = new OnboardingProbeProperties();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(40_000L);

        ResponseEntity<Object> response = controller(disabled).probe(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .isEqualTo(ProbeErrorResponse.of("PROBE_DISABLED", "OPC UA probe is disabled"));
        verify(request, never()).getInputStream();
        verifyNoInteractions(probeService);
    }

    @Test
    void declaredLengthAboveTheCapIs413WithTheStreamUnread() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(40_000L);

        ResponseEntity<Object> response = controller(enabled()).probe(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        verify(request, never()).getInputStream();
        verifyNoInteractions(probeService);
    }

    @Test
    void lengthLessStreamOfOneByteOverTheCapIs413() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getInputStream())
                .thenReturn(
                        new DelegatingServletInputStream(
                                new ByteArrayInputStream(new byte[32_769])));

        ResponseEntity<Object> response = controller(enabled()).probe(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody())
                .isEqualTo(ProbeErrorResponse.of("BODY_TOO_LARGE", "probe body exceeds 32KiB"));
        verifyNoInteractions(probeService);
    }

    private OnboardingProbeController controller(OnboardingProbeProperties properties) {
        return new OnboardingProbeController(properties, mapperConfig, probeService);
    }

    private static OnboardingProbeProperties enabled() {
        OnboardingProbeProperties properties = new OnboardingProbeProperties();
        properties.setProbeEnabled(true);
        return properties;
    }

    private static OnboardingProbeResponse okResponse() {
        return new OnboardingProbeResponse(
                "OPC.TCP://localhost:53530/OPCUA/SimulationServer", "None", true, null, List.of());
    }
}
