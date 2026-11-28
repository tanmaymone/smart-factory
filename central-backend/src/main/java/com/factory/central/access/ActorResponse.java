package com.factory.central.access;

public record ActorResponse(String actorId, String displayName, String role, boolean enabled) {

    public static ActorResponse from(ActorEntity entity) {
        return new ActorResponse(
                entity.getActorId(), entity.getDisplayName(), entity.getRole().name(), entity.isEnabled());
    }
}
