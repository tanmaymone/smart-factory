package com.factory.central.access;

import java.time.Instant;
import java.util.UUID;

public record EdgeCredentialSummaryResponse(
        UUID id,
        String siteId,
        String label,
        String tokenPrefix,
        String createdBy,
        Instant createdAt,
        Instant revokedAt,
        boolean active) {

    public static EdgeCredentialSummaryResponse from(EdgeSiteCredentialEntity entity) {
        return new EdgeCredentialSummaryResponse(
                entity.getId(),
                entity.getSiteId(),
                entity.getLabel(),
                entity.getTokenPrefix(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getRevokedAt(),
                entity.isActive());
    }
}