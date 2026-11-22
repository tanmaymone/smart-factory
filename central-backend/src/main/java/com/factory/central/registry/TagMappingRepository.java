package com.factory.central.registry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagMappingRepository extends JpaRepository<TagMapping, UUID> {

    Optional<TagMapping> findBySiteIdAndLineIdAndTagCode(String siteId, String lineId, String tagCode);

    List<TagMapping> findBySiteIdAndLineIdAndExternalAddress(String siteId, String lineId, String externalAddress);

    @Query("""
            SELECT t FROM TagMapping t
            WHERE (:siteId IS NULL OR t.siteId = :siteId)
              AND (:lineId IS NULL OR t.lineId = :lineId)
              AND (:protocol IS NULL OR t.protocol = :protocol)
              AND (:writable IS NULL OR t.writable = :writable)
            ORDER BY t.siteId ASC, t.lineId ASC, t.tagCode ASC
            """)
    List<TagMapping> findFiltered(
            @Param("siteId") String siteId,
            @Param("lineId") String lineId,
            @Param("protocol") Protocol protocol,
            @Param("writable") Boolean writable);
}
