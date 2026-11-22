package com.factory.central.command;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommandAuditLogRepository extends JpaRepository<CommandAuditLog, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE command_audit_logs
                    SET status = :status,
                        applied_value = :appliedValue,
                        rejection_reason = :rejectionReason,
                        acked_at = :ackedAt
                    WHERE command_id = :commandId
                      AND status = 'VALIDATED_DISPATCHED'
                    """,
            nativeQuery = true)
    int transitionFromDispatched(
            @Param("commandId") UUID commandId,
            @Param("status") String status,
            @Param("appliedValue") String appliedValue,
            @Param("rejectionReason") String rejectionReason,
            @Param("ackedAt") Instant ackedAt);

    Optional<CommandAuditLog> findByCommandId(UUID commandId);

    @Query("""
            SELECT c FROM CommandAuditLog c
            WHERE (:siteId IS NULL OR c.siteId = :siteId)
              AND (:lineId IS NULL OR c.lineId = :lineId)
              AND (:tagCode IS NULL OR c.tagCode = :tagCode)
              AND (:status IS NULL OR c.status = :status)
            ORDER BY c.createdAt DESC
            """)
    List<CommandAuditLog> findFiltered(
            @Param("siteId") String siteId,
            @Param("lineId") String lineId,
            @Param("tagCode") String tagCode,
            @Param("status") CommandStatus status,
            Pageable pageable);
}
