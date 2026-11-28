package com.factory.central.access;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {

    @Query(
            """
            SELECT s FROM AuthSessionEntity s
            WHERE s.tokenHash = :tokenHash
              AND s.revokedAt IS NULL
              AND s.expiresAt > :now
            """)
    Optional<AuthSessionEntity> findActiveByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE AuthSessionEntity s SET s.revokedAt = :at WHERE s.tokenHash = :tokenHash AND s.revokedAt IS NULL")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("at") Instant at);

    @Modifying
    @Query("UPDATE AuthSessionEntity s SET s.revokedAt = :at WHERE s.actorId = :actorId AND s.revokedAt IS NULL")
    int revokeAllForActor(@Param("actorId") String actorId, @Param("at") Instant at);

    @Modifying
    @Query("DELETE FROM AuthSessionEntity s WHERE s.expiresAt < :before OR s.revokedAt IS NOT NULL")
    int deleteExpiredOrRevoked(@Param("before") Instant before);
}