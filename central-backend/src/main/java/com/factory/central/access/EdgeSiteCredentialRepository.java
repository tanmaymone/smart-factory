package com.factory.central.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EdgeSiteCredentialRepository extends JpaRepository<EdgeSiteCredentialEntity, UUID> {

    @Query(
            """
            SELECT c FROM EdgeSiteCredentialEntity c
            WHERE c.tokenHash = :tokenHash AND c.revokedAt IS NULL
            """)
    Optional<EdgeSiteCredentialEntity> findActiveByTokenHash(@Param("tokenHash") String tokenHash);

    List<EdgeSiteCredentialEntity> findBySiteIdOrderByCreatedAtDesc(String siteId);

    @Query(
            """
            SELECT c FROM EdgeSiteCredentialEntity c
            WHERE c.siteId = :siteId AND c.tokenHash = :tokenHash AND c.revokedAt IS NULL
            """)
    Optional<EdgeSiteCredentialEntity> findActiveBySiteAndHash(
            @Param("siteId") String siteId, @Param("tokenHash") String tokenHash);
}