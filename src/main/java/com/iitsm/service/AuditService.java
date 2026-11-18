package com.iitsm.service;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.AuditEntry;
import com.iitsm.domain.observer.AuditObserver;
import com.iitsm.repository.AuditEntryRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service wiring AuditObserver persistence and exposing immutable audit log queries. */
@Service
@Transactional
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;
    private final AuditObserver auditObserver;

    /** Constructs AuditService and connects it to AuditObserver. */
    public AuditService(AuditEntryRepository auditEntryRepository, AuditObserver auditObserver) {
        this.auditEntryRepository = auditEntryRepository;
        this.auditObserver = auditObserver;
    }

    /** Wires the AuditObserver persistence callback on startup. */
    @PostConstruct
    public void initObserverSink() {
        auditObserver.setPersistenceSink(auditEntryRepository::save);
    }

    /** Records an immutable audit entry directly for non-event system actions. */
    public AuditEntry recordAction(String actor, String action, String incidentId,
                                   IncidentStatus prevState, IncidentStatus newState, String description) {
        AuditEntry entry = new AuditEntry(
                Instant.now(), actor, action, incidentId, prevState, newState, description);
        return auditEntryRepository.save(entry);
    }

    /** Returns the chronological audit timeline for a single incident. */
    @Transactional(readOnly = true)
    public List<AuditEntry> getTimelineForIncident(String incidentId) {
        return auditEntryRepository.findByIncidentIdOrderByTimestampAsc(incidentId);
    }

    /** Searches global audit entries with optional incident ID, actor, and action filters. */
    @Transactional(readOnly = true)
    public Page<AuditEntry> search(String incidentId, String actor, String action, Pageable pageable) {
        String idFilter = normalizeBlank(incidentId);
        String actorFilter = normalizeBlank(actor);
        String actionFilter = normalizeBlank(action);
        return auditEntryRepository.search(idFilter, actorFilter, actionFilter, pageable);
    }

    private String normalizeBlank(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
