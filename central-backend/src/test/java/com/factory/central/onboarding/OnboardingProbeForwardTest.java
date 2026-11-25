package com.factory.central.onboarding;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Forward copies edge 200/409/422 only when the media type is application/json; every other HTTP
 * result is 502 and a failed HTTP call is 503. The forwarded entity is the original request bytes.
 */
@Tag("EDGE-PROBE")
class OnboardingProbeForwardTest {

    private static final String PATH = "/api/onboarding/probe";
    /** Odd spacing and key order on purpose: a re-serialized DTO would not match these bytes. */
    private static final byte[] REQUEST_BYTES =
            ("{ \"securityMode\" : \"None\" ,\n  \"nodeIds\" : [ \"ns=0;i=2259\" ],\n"
                            + "  \"endpointUrl\":\"opc.tcp://localhost:53530/OPCUA/SimulationServer\" }")
                    .getBytes(UTF_8);

    private StubEdgeServer edge;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        edge = StubEdgeServer.start();
        mockMvc = mockMvcFor(edge.baseUrl());
    }

    @AfterEach
    void tearDown() {
        edge.close();
    }

    @Test
    void copiesEdge200AndPostsTheOriginalBytes() throws Exception {
        edge.respondWith(200, "application/json;charset=UTF-8", "{\"reachable\":true,\"tags\":[]}");

        mockMvc.perform(probe())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"reachable\":true,\"tags\":[]}"));

        assertThat(edge.requests()).hasSize(1);
        assertThat(edge.requests().get(0)).isEqualTo(REQUEST_BYTES);
    }

    @Test
    void copiesEdge409And422() throws Exception {
        edge.respondWith(409, "application/json", "{\"code\":\"PROBE_IN_PROGRESS\",\"message\":\"busy\"}");
        mockMvc.perform(probe())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROBE_IN_PROGRESS"));

        edge.respondWith(422, "application/json", "{\"code\":\"ENDPOINT_URL_INVALID\",\"message\":\"x\"}");
        mockMvc.perform(probe())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ENDPOINT_URL_INVALID"));
    }

    @Test
    void nonJsonEdge200IsBadGateway() throws Exception {
        edge.respondWith(200, "text/html", "<html>wrong process on 8081</html>");

        mockMvc.perform(probe())
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("EDGE_BAD_RESPONSE"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("200")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("wrong process"))));
    }

    @Test
    void edge200WithoutContentTypeIsBadGateway() throws Exception {
        edge.respondWith(200, null, "{\"reachable\":true}");

        mockMvc.perform(probe())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EDGE_BAD_RESPONSE"));
    }

    @Test
    void edgeStatusOutsideTheCopyListIsBadGateway() throws Exception {
        edge.respondWith(500, "application/json", "{\"error\":\"boom\"}");
        mockMvc.perform(probe())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EDGE_BAD_RESPONSE"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("500")));

        edge.respondWith(403, "application/json", "{\"code\":\"PROBE_DISABLED\"}");
        mockMvc.perform(probe())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EDGE_BAD_RESPONSE"));

        edge.respondWith(400, "application/json", "{\"code\":\"INVALID_REQUEST_BODY\"}");
        mockMvc.perform(probe())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EDGE_BAD_RESPONSE"));
    }

    @Test
    void refusedConnectionIsServiceUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        mockMvcFor("http://127.0.0.1:" + closedPort)
                .perform(probe())
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("EDGE_UNREACHABLE"));
    }

    private static MockMvc mockMvcFor(String edgeBaseUrl) {
        OnboardingProbeProperties properties = ProbeTestSupport.properties(true, edgeBaseUrl);
        return ProbeTestSupport.mockMvc(properties, new OnboardingProbeForwarder(properties));
    }

    private static RequestBuilder probe() {
        return post(PATH)
                .header(ProbeTestSupport.setupTokenHeader(), ProbeTestSupport.ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BYTES);
    }
}
