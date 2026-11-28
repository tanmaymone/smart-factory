package com.factory.central.access;

public record AuthUserResponse(String actorId, String displayName, String role) {

    public static AuthUserResponse from(ActorContext actor) {
        return new AuthUserResponse(actor.actorId(), actor.displayName(), actor.role().name());
    }
}