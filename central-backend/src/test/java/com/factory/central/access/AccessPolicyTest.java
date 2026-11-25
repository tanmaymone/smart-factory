package com.factory.central.access;

import static org.assertj.core.api.Assertions.assertThat;

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
                AccessPolicy.evaluate(AccessAction.ACTOR_ADMIN, new ActorResolution.Resolved(supervisor)),
                HttpStatus.FORBIDDEN);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.ONBOARDING_PROBE, new ActorResolution.Resolved(operator)),
                HttpStatus.FORBIDDEN);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.ONBOARDING_PROBE, new ActorResolution.Resolved(supervisor)),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void onboardingAdminCanManageRegistryActorsAndProbeOnly() {
        ActorContext admin = new ActorContext("admin", "Admin", ActorRole.ONBOARDING_ADMIN);

        assertThat(AccessPolicy.evaluate(AccessAction.REGISTRY_WRITE, new ActorResolution.Resolved(admin)))
                .isInstanceOf(AccessPolicy.Allow.class);
        assertThat(AccessPolicy.evaluate(AccessAction.ACTOR_ADMIN, new ActorResolution.Resolved(admin)))
                .isInstanceOf(AccessPolicy.Allow.class);
        assertThat(AccessPolicy.evaluate(AccessAction.ONBOARDING_PROBE, new ActorResolution.Resolved(admin)))
                .isInstanceOf(AccessPolicy.Allow.class);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, new ActorResolution.Resolved(admin)),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void viewerAndAnonymousAreDeniedWrites() {
        ActorContext viewer = new ActorContext("viewer", "Viewer", ActorRole.VIEWER);

        assertDenied(
                AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, new ActorResolution.Resolved(viewer)),
                HttpStatus.FORBIDDEN);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.REGISTRY_WRITE, new ActorResolution.Resolved(viewer)),
                HttpStatus.FORBIDDEN);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.COMMAND_WRITE, new ActorResolution.Anonymous()),
                HttpStatus.UNAUTHORIZED);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.REGISTRY_WRITE, new ActorResolution.Anonymous()),
                HttpStatus.UNAUTHORIZED);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.ONBOARDING_PROBE, new ActorResolution.Resolved(viewer)),
                HttpStatus.FORBIDDEN);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.ONBOARDING_PROBE, new ActorResolution.Anonymous()),
                HttpStatus.UNAUTHORIZED);
        assertDenied(
                AccessPolicy.evaluate(AccessAction.ONBOARDING_PROBE, new ActorResolution.Rejected()),
                HttpStatus.UNAUTHORIZED);
    }

    private static void assertDenied(AccessPolicy.Decision decision, HttpStatus status) {
        assertThat(decision).isInstanceOf(AccessPolicy.Deny.class);
        AccessPolicy.Deny deny = (AccessPolicy.Deny) decision;
        assertThat(deny.status()).isEqualTo(status);
    }
}
