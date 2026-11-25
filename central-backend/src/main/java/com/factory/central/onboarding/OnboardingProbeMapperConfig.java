package com.factory.central.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OnboardingProbeProperties.class)
public class OnboardingProbeMapperConfig {

    /**
     * Copy of the application mapper that fails Integer, Float, and Boolean into a textual slot, so
     * {@code "securityMode": 1} is 400 INVALID_REQUEST_BODY instead of a coerced "1" that would be
     * 422. Disabling ALLOW_COERCION_OF_SCALARS does not do this. The application mapper is unchanged.
     */
    @Bean
    public OnboardingProbeMapper onboardingProbeMapper(ObjectMapper objectMapper) {
        ObjectMapper probeMapper = objectMapper.copy();
        probeMapper
                .coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return new OnboardingProbeMapper(probeMapper);
    }
}
