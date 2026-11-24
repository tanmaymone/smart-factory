package com.factory.central.access;

public sealed interface ActorResolution
        permits ActorResolution.Anonymous, ActorResolution.Resolved, ActorResolution.Rejected {

    record Anonymous() implements ActorResolution {}

    record Resolved(ActorContext actor) implements ActorResolution {}

    record Rejected() implements ActorResolution {}
}
