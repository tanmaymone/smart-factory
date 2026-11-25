package com.factory.edge.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.stereotype.Component;

/**
 * Holds the one mapper copy the probe controller binds with. Exposing it as an {@code ObjectMapper}
 * bean would make Boot's primary mapper back off, so it is held rather than published.
 *
 * <p>{@link LogicalType#Textual} is the target of a scalar {@code String} and of each
 * {@code List<String>} element, so {@code 1}, {@code 1.5}, and {@code true} in {@code securityMode},
 * {@code endpointUrl}, or a {@code nodeIds} element fail as 400 instead of coercing into a string
 * that would then read as 422 or as an ordinary node id.
 */
@Component
public class OnboardingProbeMapperConfig {

    private final ObjectMapper probeMapper;

    public OnboardingProbeMapperConfig(ObjectMapper objectMapper) {
        ObjectMapper copy = objectMapper.copy();
        copy.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        this.probeMapper = copy;
    }

    public ObjectMapper probeMapper() {
        return probeMapper;
    }
}
