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
        return switch (resolution) {
            case ActorResolution.Rejected ignored ->
                    new Deny(HttpStatus.UNAUTHORIZED, AccessErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
            case ActorResolution.Anonymous ignored -> evaluateAnonymous(action);
            case ActorResolution.EdgeSite edge -> evaluateEdge(action, edge);
            case ActorResolution.Resolved resolved -> evaluateActor(action, resolved.actor());
        };
    }

    public static Decision evaluateEdgeConfig(
            AccessAction action, ActorResolution resolution, String targetSiteId) {
        if (action != AccessAction.EDGE_CONFIG_READ) {
            return evaluate(action, resolution);
        }
        return switch (resolution) {
            case ActorResolution.Rejected ignored ->
                    new Deny(HttpStatus.UNAUTHORIZED, AccessErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
            case ActorResolution.Anonymous ignored ->
                    new Deny(HttpStatus.UNAUTHORIZED, AccessErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
            case ActorResolution.Resolved ignored ->
                    new Deny(HttpStatus.FORBIDDEN, AccessErrorCode.FORBIDDEN, FORBIDDEN_MESSAGE);
            case ActorResolution.EdgeSite edge -> {
                if (targetSiteId == null
                        || targetSiteId.isBlank()
                        || !edge.siteId().equals(targetSiteId)) {
                    yield new Deny(
                            HttpStatus.FORBIDDEN,
                            AccessErrorCode.EDGE_SITE_FORBIDDEN,
                            "Edge credential is not permitted for this site");
                }
                yield new Allow(ActorContext.anonymous());
            }
        };
    }

    private static Decision evaluateAnonymous(AccessAction action) {
        if (action == AccessAction.OPEN) {
            return new Allow(ActorContext.anonymous());
        }
        return new Deny(HttpStatus.UNAUTHORIZED, AccessErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
    }

    private static Decision evaluateEdge(AccessAction action, ActorResolution.EdgeSite edge) {
        if (action == AccessAction.EDGE_CONFIG_READ) {
            return new Allow(ActorContext.anonymous());
        }
        return new Deny(HttpStatus.FORBIDDEN, AccessErrorCode.FORBIDDEN, FORBIDDEN_MESSAGE);
    }

    private static Decision evaluateActor(AccessAction action, ActorContext actor) {
        ActorRole role = actor.role();
        boolean allowed =
                switch (action) {
                    case OPEN -> true;
                    case AUTHENTICATED_READ -> role != null;
                    case REGISTRY_WRITE, ACTOR_ADMIN, ONBOARDING_PROBE, TELEMETRY_INGEST ->
                            role == ActorRole.ONBOARDING_ADMIN;
                    case COMMAND_WRITE -> role == ActorRole.OPERATOR || role == ActorRole.SUPERVISOR;
                    case EDGE_CONFIG_READ -> false;
                };
        if (allowed) {
            return new Allow(actor);
        }
        return new Deny(HttpStatus.FORBIDDEN, AccessErrorCode.FORBIDDEN, FORBIDDEN_MESSAGE);
    }
}