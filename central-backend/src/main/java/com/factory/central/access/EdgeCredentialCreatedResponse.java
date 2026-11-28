package com.factory.central.access;

import java.time.Instant;
import java.util.UUID;

public record EdgeCredentialCreatedResponse(
        UUID id, String siteId, String label, String tokenPrefix, String token, Instant createdAt) {}