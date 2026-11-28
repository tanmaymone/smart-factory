package com.factory.central.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Tag("SF-ACCESS")
class AccessPolicyTest {

    @Test
    void operatorAndSupervisorCanWriteCommandsOnly() {
        ActorContext operator = new ActorContext("op", "Op", ActorRole.OPERATOR);
        ActorContext supervisor = new ActorContext("sup", "Sup", ActorRole.SUPERVISOR);

        assertThat(AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, new ActorResolution.Resolved(operator)))
                .isInstanceOf(AccessPolicy.Allow.class);
        assertThat(AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, new ActorResolution.Resolved(supervisor)))
                .isInstanceOf(AccessPolicy.Allow.class);

        assertDenied(
                AccessPolicy.evaluate(AccessAction.REGISTRY_WRITE, new ActorResolution.Resolved(operator)),
                HttpStatus.FORBIDDEN);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.AUTHENTICATED_READ, new ActorResolution.EdgeSite(UUID.randomUUID(), "INDORE")),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void onboardingAdminCanManageRegistryActorsAndProbeOnly() {
        ActorContext admin = new ActorContext("admin", "Admin", ActorRole.ONBOARDING_ADMIN);

        assertThat(AccessPolicy.evaluate(AccessAction.REGISTRY_WRITE, new ActorResolution.Resolved(admin)))
                .isInstanceOf(AccessPolicy.Allow.class);
        assertThat(AccessPolicy.evaluate(AccessAction.TELEMETRY_INGEST, new ActorResolution.Resolved(admin)))
                .isInstanceOf(AccessPolicy.Allow.class);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, new ActorResolution.Resolved(admin)),
                HttpStatus.FORBIDDEN);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.EDGE_CONFIG_READ, new ActorResolution.Resolved(admin)),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void viewerAndAnonymousAreDeniedWrites() {
        ActorContext viewer = new ActorContext("viewer", "Viewer", ActorRole.VIEWER);

        assertDenied(
                AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, new ActorResolution.Resolved(viewer)),
                HttpStatus.FORBIDDEN);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, new ActorResolution.Anonymous()),
                HttpStatus.UNAUTHORIZED);
        assertThat(AccessPolicy.evaluate(AccessAction.AUTHENTICATED_READ, new ActorResolution.Resolved(viewer)))
                .isInstanceOf(AccessPolicy.Allow.class);
    }

    @Test
    void edgeSiteTokenBoundToConfigSite() {
        ActorResolution.EdgeSite indore = new ActorResolution.EdgeSite(UUID.randomUUID(), "INDORE");
        assertThat(AccessPolicy.evaluateEdgeConfig(AccessAction.EDGE_CONFIG_READ, indore, "INDORE"))
                .isInstanceOf(AccessPolicy.Allow.class);
        assertDenied(
                AccessPolicy.evaluateEdgeConfig(AccessAction.EDGE_CONFIG_READ, indore, "PUNE"),
                HttpStatus.FORBIDDEN);
        assertThat(AccessPolicy.evaluate(AccessAction.EDGE_CONFIG_READ, indore)).isInstanceOf(AccessPolicy.Allow.class);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, indore), HttpStatus.FORBIDDEN);
    }

    private static void assertDenied(AccessPolicy.Decision decision, HttpStatus status) {
        assertThat(decision).isInstanceOf(AccessPolicy.Deny.class);
        AccessPolicy.Deny deny = (AccessPolicy.Deny) decision;
        assertThat(deny.status()).isEqualTo(status);
    }
}