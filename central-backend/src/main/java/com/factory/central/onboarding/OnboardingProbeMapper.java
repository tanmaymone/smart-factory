package com.factory.central.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Holder for the probe-only mapper copy. It is deliberately not an {@code ObjectMapper} bean: any
 * bean of that type makes Spring Boot's auto-configured primary mapper back off, which would hand
 * this CoercionConfig copy to every HTTP message converter in the application.
 */
public record OnboardingProbeMapper(ObjectMapper mapper) {

    /**
     * Throws {@link com.fasterxml.jackson.core.JacksonException} for a body the DTO cannot bind.
     * Root JSON {@code null} does not throw: {@code readValue} returns Java null, and the controller
     * rejects that as 400.
     */
    public OnboardingProbeRequest read(byte[] body) throws IOException {
        return mapper.readValue(body, OnboardingProbeRequest.class);
    }
}
