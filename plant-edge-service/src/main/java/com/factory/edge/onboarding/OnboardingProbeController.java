package com.factory.edge.onboarding;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Edge probe entry point. The method takes {@link HttpServletRequest} and has no {@code @RequestBody}
 * — Spring resolves {@code @RequestBody} before the method, so the flag and the 413 cap would be too
 * late and the global message converter would bind with the application mapper instead of
 * {@code probeMapper}. There is no body-reading interceptor and no {@code ContentCachingRequestWrapper}.
 *
 * <p>Gate order, and a later gate does not run: flag (403, stream unread), size (413, before
 * binding), bind (400), semantics (422), then {@code tryLock} (409). A 403, 413, 400, or 422 never
 * takes the lock and never starts the 8s deadline.
 */
@RestController
public class OnboardingProbeController {

    private static final int MAX_BODY_BYTES = 32_768;

    private final OnboardingProbeProperties properties;
    private final ObjectMapper probeMapper;
    private final OnboardingProbeService probeService;

    public OnboardingProbeController(
            OnboardingProbeProperties properties,
            OnboardingProbeMapperConfig mapperConfig,
            OnboardingProbeService probeService) {
        this.properties = properties;
        this.probeMapper = mapperConfig.probeMapper();
        this.probeService = probeService;
    }

    @PostMapping("/api/onboarding/probe")
    public ResponseEntity<Object> probe(HttpServletRequest request) throws IOException {
        if (!properties.isProbeEnabled()) {
            return json(
                    HttpStatus.FORBIDDEN,
                    ProbeErrorResponse.of("PROBE_DISABLED", "OPC UA probe is disabled"));
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            return bodyTooLarge();
        }
        byte[] body = readAtMost(request.getInputStream(), MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            return bodyTooLarge();
        }

        final OnboardingProbeRequest dto;
        try {
            dto = probeMapper.readValue(body, OnboardingProbeRequest.class);
        } catch (JacksonException e) {
            return invalidRequestBody();
        }
        // Root JSON null is valid JSON and readValue returns Java null without throwing.
        if (dto == null) {
            return invalidRequestBody();
        }

        OnboardingProbeValidator.Validation validation = OnboardingProbeValidator.validate(dto);
        if (validation.rejected()) {
            return json(HttpStatus.UNPROCESSABLE_ENTITY, validation.error());
        }

        return probeService
                .probe(dto, validation.url())
                .map(response -> json(HttpStatus.OK, response))
                .orElseGet(
                        () ->
                                json(
                                        HttpStatus.CONFLICT,
                                        ProbeErrorResponse.of(
                                                "PROBE_IN_PROGRESS",
                                                "another OPC UA probe is in progress")));
    }

    /** Stops at {@code limit} bytes and does not drain the rest of a larger stream. */
    private static byte[] readAtMost(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        while (total < limit) {
            int read = in.read(chunk, 0, Math.min(chunk.length, limit - total));
            if (read < 0) {
                break;
            }
            buffer.write(chunk, 0, read);
            total += read;
        }
        return buffer.toByteArray();
    }

    private static ResponseEntity<Object> invalidRequestBody() {
        return json(
                HttpStatus.BAD_REQUEST,
                ProbeErrorResponse.of("INVALID_REQUEST_BODY", "probe body is invalid"));
    }

    private static ResponseEntity<Object> bodyTooLarge() {
        return json(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ProbeErrorResponse.of("BODY_TOO_LARGE", "probe body exceeds 32KiB"));
    }

    private static ResponseEntity<Object> json(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
