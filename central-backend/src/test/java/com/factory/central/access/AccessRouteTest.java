package com.factory.central.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-ACCESS")
class AccessRouteTest {

    @Test
    void classifiesOpenAndGatedRoutes() {
        assertThat(AccessRoute.classify("GET", "/api/registry/sites")).contains(AccessAction.OPEN);
        assertThat(AccessRoute.classify("GET", "/api/edge/INDORE/config")).contains(AccessAction.OPEN);
        assertThat(AccessRoute.classify("HEAD", "/api/edge/INDORE/config")).contains(AccessAction.OPEN);
        assertThat(AccessRoute.classify("GET", "/api/commands/audit")).contains(AccessAction.OPEN);
        assertThat(AccessRoute.classify("POST", "/api/telemetry/ingest")).contains(AccessAction.OPEN);
        assertThat(AccessRoute.classify("GET", "/api/stream/telemetry")).contains(AccessAction.OPEN);
        assertThat(AccessRoute.classify("GET", "/actuator/health")).contains(AccessAction.OPEN);

        assertThat(AccessRoute.classify("POST", "/api/registry/sites")).contains(AccessAction.REGISTRY_WRITE);
        assertThat(AccessRoute.classify("POST", "/api/registry/not-a-resource"))
                .contains(AccessAction.REGISTRY_WRITE);
        assertThat(AccessRoute.classify("POST", "/api/commands")).contains(AccessAction.COMMAND_WRITE);
        assertThat(AccessRoute.classify("GET", "/api/access/actors")).contains(AccessAction.ACTOR_ADMIN);
        assertThat(AccessRoute.classify("POST", "/api/onboarding/probe"))
                .contains(AccessAction.ONBOARDING_PROBE);
    }

    @Test
    void normalizesTrailingAndDoubleSlashes() {
        assertThat(AccessRoute.classify("POST", "/api/registry/sites/")).contains(AccessAction.REGISTRY_WRITE);
        assertThat(AccessRoute.classify("POST", "/api//registry/sites")).contains(AccessAction.REGISTRY_WRITE);
        assertThat(AccessRoute.classify("POST", "/api/commands/")).contains(AccessAction.COMMAND_WRITE);
        assertThat(AccessRoute.classify("GET", "/api/access/actors/")).contains(AccessAction.ACTOR_ADMIN);
        assertThat(AccessRoute.classify("POST", "/api/onboarding/probe/"))
                .contains(AccessAction.ONBOARDING_PROBE);
        assertThat(AccessRoute.classify("POST", "/api//onboarding/probe"))
                .contains(AccessAction.ONBOARDING_PROBE);
    }

    @Test
    void leavesUnmatchedPathsOpenToSpring() {
        assertThat(AccessRoute.classify("POST", "/api/does-not-exist")).isEqualTo(Optional.empty());
        assertThat(AccessRoute.classify("DELETE", "/api/commands")).isEqualTo(Optional.empty());
        assertThat(AccessRoute.classify("GET", "/api/onboarding/probe")).isEqualTo(Optional.empty());
        assertThat(AccessRoute.classify("POST", "/api/onboarding")).isEqualTo(Optional.empty());
        assertThat(AccessRoute.classify("POST", "/api/onboarding/probe/extra")).isEqualTo(Optional.empty());
        assertThat(AccessRoute.classify("GET", "/api/edge/config")).isEqualTo(Optional.empty());
        assertThat(AccessRoute.classify("GET", "/api/edge/INDORE/config/extra")).isEqualTo(Optional.empty());
        assertThat(AccessRoute.classify("POST", "/api/edge/INDORE/config")).isEqualTo(Optional.empty());
    }
}
