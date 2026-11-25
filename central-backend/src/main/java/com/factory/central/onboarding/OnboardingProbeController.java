package com.factory.central.onboarding;

import com.fasterxml.jackson.core.JacksonException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gate order after AccessInterceptor (401 / 403 FORBIDDEN): flag, 413, 400, 422, forward. A later
 * gate does not run. No {@code @RequestBody}: Spring would resolve it before the flag and the size
 * cap, and the global converter would ignore the probe mapper's CoercionConfig.
 */
@RestController
public class OnboardingProbeController {

    static final int MAX_BODY_BYTES = 32_768;

    private final OnboardingProbeProperties properties;
    private final OnboardingProbeMapper probeMapper;
    private final OnboardingProbeForwarder forwarder;

    public OnboardingProbeController(
            OnboardingProbeProperties properties,
            OnboardingProbeMapper probeMapper,
            OnboardingProbeForwarder forwarder) {
        this.properties = properties;
        this.probeMapper = probeMapper;
        this.forwarder = forwarder;
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
            dto = probeMapper.read(body);
        } catch (JacksonException ex) {
            return invalidRequestBody();
        }
        // Root JSON null is valid JSON and readValue returns Java null without throwing.
        if (dto == null) {
            return invalidRequestBody();
        }

        Optional<ProbeErrorResponse> invalid = OnboardingProbeValidator.validate(dto);
        if (invalid.isPresent()) {
            return json(HttpStatus.UNPROCESSABLE_ENTITY, invalid.get());
        }
        return forwarder.forward(body);
    }

    /** Stops at {@code limit} bytes and does not drain the rest of a larger stream. */
    private static byte[] readAtMost(InputStream stream, int limit) throws IOException {
        byte[] buffer = new byte[limit];
        int read = 0;
        while (read < limit) {
            int count = stream.read(buffer, read, limit - read);
            if (count < 0) {
                break;
            }
            read += count;
        }
        return read == limit ? buffer : Arrays.copyOf(buffer, read);
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

    private static ResponseEntity<Object> json(HttpStatus status, ProbeErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
