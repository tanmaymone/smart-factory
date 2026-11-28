package com.factory.central.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edge_site_credentials")
public class EdgeSiteCredentialEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "site_id", nullable = false, length = 64)
    private String siteId;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected EdgeSiteCredentialEntity() {}

    public EdgeSiteCredentialEntity(
            UUID id,
            String siteId,
            String label,
            String tokenPrefix,
            String tokenHash,
            String createdBy,
            Instant createdAt) {
        this.id = id;
        this.siteId = siteId;
        this.label = label;
        this.tokenPrefix = tokenPrefix;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSiteId() {
        return siteId;
    }

    public String getLabel() {
        return label;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}