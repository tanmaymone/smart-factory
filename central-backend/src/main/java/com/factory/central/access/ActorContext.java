package com.factory.central.access;

public record ActorContext(String actorId, String displayName, ActorRole role) {

    public static ActorContext anonymous() {
        return new ActorContext(null, null, null);
    }
}
