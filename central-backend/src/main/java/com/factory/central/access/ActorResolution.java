package com.factory.central.access;

public sealed interface ActorResolution
        permits ActorResolution.Anonymous, ActorResolution.Resolved, ActorResolution.EdgeSite, ActorResolution.Rejected {

    record Anonymous() implements ActorResolution {}

    record Resolved(ActorContext actor) implements ActorResolution {}

  record EdgeSite(java.util.UUID credentialId, String siteId) implements ActorResolution {}

    record Rejected() implements ActorResolution {}
}
