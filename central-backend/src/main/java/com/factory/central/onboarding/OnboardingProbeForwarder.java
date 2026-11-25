package com.factory.central.onboarding;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Posts the validated request bytes to the single local edge. The edge dials OPC UA; Central never
 * opens the submitted endpoint. Edge 200/409/422 are copied only when the media type is
 * application/json; every other HTTP result is 502 and no HTTP response at all is 503.
 */
@Component
public class OnboardingProbeForwarder {

    public static final String EDGE_BAD_RESPONSE = "EDGE_BAD_RESPONSE";
    public static final String EDGE_UNREACHABLE = "EDGE_UNREACHABLE";

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final String probeUri;

    public OnboardingProbeForwarder(OnboardingProbeProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                // 4xx and 5xx must come back as a response, not as a thrown 500 or 503.
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
        this.probeUri = properties.getEdgeBaseUrl() + "/api/onboarding/probe";
    }

    /** {@code body} is the byte[] that passed the size, binding, and 422 gates, not a re-serialized DTO. */
    public ResponseEntity<Object> forward(byte[] body) {
        ResponseEntity<byte[]> edge;
        try {
            edge = restClient
                    .post()
                    .uri(probeUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (ResourceAccessException ex) {
            return error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    EDGE_UNREACHABLE,
                    "Edge probe service is unreachable");
        }

        int status = edge.getStatusCode().value();
        if (isCopyable(status) && isJson(edge)) {
            byte[] edgeBody = edge.getBody() == null ? new byte[0] : edge.getBody();
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(edgeBody);
        }
        return error(
                HttpStatus.BAD_GATEWAY,
                EDGE_BAD_RESPONSE,
                "Edge probe service returned HTTP " + status + " with content type " + contentTypeText(edge));
    }

    private static boolean isCopyable(int status) {
        return status == 200 || status == 409 || status == 422;
    }

    private static boolean isJson(ResponseEntity<byte[]> edge) {
        try {
            MediaType contentType = edge.getHeaders().getContentType();
            return contentType != null && MediaType.APPLICATION_JSON.equalsTypeAndSubtype(contentType);
        } catch (InvalidMediaTypeException ex) {
            return false;
        }
    }

    private static String contentTypeText(ResponseEntity<byte[]> edge) {
        String raw = edge.getHeaders().getFirst("Content-Type");
        return raw == null || raw.isBlank() ? "none" : raw;
    }

    private static ResponseEntity<Object> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ProbeErrorResponse.of(code, message));
    }
}
