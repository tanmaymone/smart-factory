package com.factory.central.onboarding;

import com.factory.central.access.AccessInterceptor;
import com.factory.central.access.ActorContext;
import com.factory.central.access.ActorResolution;
import com.factory.central.access.ActorResolver;
import com.factory.central.access.ActorRole;
import com.factory.central.access.BootstrapActorResolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Path;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Wires the probe controller behind the real AccessInterceptor, as production does. */
final class ProbeTestSupport {

    static final String ADMIN_TOKEN = "setup-token";
    static final String OPERATOR_ACTOR = "operator-1";

    private ProbeTestSupport() {}

    static MockMvc mockMvc(OnboardingProbeProperties properties, OnboardingProbeForwarder forwarder) {
        OnboardingProbeController controller =
                new OnboardingProbeController(properties, probeMapper(), forwarder);
        AccessInterceptor interceptor = new AccessInterceptor(resolver(), new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(interceptor)
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(applicationMapper()))
                .build();
    }

    static OnboardingProbeProperties properties(boolean probeEnabled, String edgeBaseUrl) {
        OnboardingProbeProperties properties = new OnboardingProbeProperties();
        properties.setProbeEnabled(probeEnabled);
        properties.setEdgeBaseUrl(edgeBaseUrl);
        return properties;
    }

    static OnboardingProbeMapper probeMapper() {
        return new OnboardingProbeMapperConfig().onboardingProbeMapper(applicationMapper());
    }

    /** Matches spring.jackson.default-property-inclusion=always from application.yml. */
    static ObjectMapper applicationMapper() {
        return new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
    }

    static String setupTokenHeader() {
        return BootstrapActorResolver.SETUP_TOKEN_HEADER;
    }

    static String actorIdHeader() {
        return BootstrapActorResolver.ACTOR_ID_HEADER;
    }

    /** Walks parent directories until contracts/onboarding-probe-validation.json exists. */
    static File validationFixture() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            File fixture = candidate.resolve("contracts/onboarding-probe-validation.json").toFile();
            if (fixture.isFile()) {
                return fixture;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "contracts/onboarding-probe-validation.json not found above " + Path.of("").toAbsolutePath());
    }

    private static ActorResolver resolver() {
        return request -> {
            String token = request.getHeader(BootstrapActorResolver.SETUP_TOKEN_HEADER);
            if (token != null && !token.isEmpty()) {
                return ADMIN_TOKEN.equals(token)
                        ? new ActorResolution.Resolved(
                                new ActorContext("admin", "Admin", ActorRole.ONBOARDING_ADMIN))
                        : new ActorResolution.Rejected();
            }
            String actorId = request.getHeader(BootstrapActorResolver.ACTOR_ID_HEADER);
            if (OPERATOR_ACTOR.equals(actorId)) {
                return new ActorResolution.Resolved(
                        new ActorContext(OPERATOR_ACTOR, "Operator", ActorRole.OPERATOR));
            }
            return new ActorResolution.Anonymous();
        };
    }
}
