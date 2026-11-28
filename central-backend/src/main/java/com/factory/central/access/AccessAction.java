package com.factory.central.access;

public enum AccessAction {
    OPEN,
    AUTHENTICATED_READ,
    REGISTRY_WRITE,
    COMMAND_WRITE,
    ACTOR_ADMIN,
    ONBOARDING_PROBE,
    TELEMETRY_INGEST,
    EDGE_CONFIG_READ
}
