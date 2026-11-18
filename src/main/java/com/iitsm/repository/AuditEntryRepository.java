package com.iitsm.repository;

import com.iitsm.domain.model.AuditEntry;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Append-only repository for immutable AuditEntry records. */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByIncidentIdOrderByTimestampAsc(String incidentId);

    Page<AuditEntry> findAllByOrderByTimestampDesc(Pageable pageable);

    @Query("""
            SELECT a FROM AuditEntry a
            WHERE (:incidentId IS NULL OR LOWER(a.incidentId) LIKE LOWER(CONCAT('%', :incidentId, '%')))
              AND (:actor IS NULL OR LOWER(a.actor) LIKE LOWER(CONCAT('%', :actor, '%')))
              AND (:action IS NULL OR LOWER(a.action) = LOWER(:action))
            ORDER BY a.timestamp DESC
            """)
    Page<AuditEntry> search(
            @Param("incidentId") String incidentId,
            @Param("actor") String actor,
            @Param("action") String action,
            Pageable pageable);
}
