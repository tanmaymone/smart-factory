package com.factory.central.access;

import org.springframework.http.HttpStatus;

public final class AccessPolicy {

    public static final String UNAUTHENTICATED_MESSAGE = "Authentication required or actor not recognized";
    public static final String FORBIDDEN_MESSAGE = "Actor role is not permitted for this operation";

    private AccessPolicy() {}

    public sealed interface Decision permits Allow, Deny {}

    public record Allow(ActorContext actor) implements Decision {}

    public record Deny(HttpStatus status, String code, String message) implements Decision {}

    public static Decision evaluate(AccessAction action, ActorResolution resolution) {
        if (action == AccessAction.OPEN) {
            return switch (resolution) {
                case ActorResolution.Rejected ignored ->
                        new Deny(HttpStatus.UNAUTHORIZED, AccessErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
                case ActorResolution.Anonymous ignored -> new Allow(ActorContext.anonymous());
                case ActorResolution.Resolved resolved -> new Allow(resolved.actor());
            };
        }

        return switch (resolution) {
            case ActorResolution.Rejected ignored ->
                    new Deny(HttpStatus.UNAUTHORIZED, AccessErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
            case ActorResolution.Anonymous ignored ->
                    new Deny(HttpStatus.UNAUTHORIZED, AccessErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
            case ActorResolution.Resolved resolved -> evaluateRole(action, resolved.actor());
        };
    }

    private static Decision evaluateRole(AccessAction action, ActorContext actor) {
        ActorRole role = actor.role();
        boolean allowed =
                switch (action) {
                    case REGISTRY_WRITE, ACTOR_ADMIN -> role == ActorRole.ONBOARDING_ADMIN;
                    case COMMAND_WRITE -> role == ActorRole.OPERATOR || role == ActorRole.SUPERVISOR;
                    case OPEN -> true;
                };
        if (allowed) {
            return new Allow(actor);
        }
        return new Deny(HttpStatus.FORBIDDEN, AccessErrorCode.FORBIDDEN, FORBIDDEN_MESSAGE);
    }
}
