package com.iitsm.domain.model;

import com.iitsm.domain.enums.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Immutable append-only audit log entry recording every incident action and state change. */
@Entity
@Table(name = "audit_entries", indexes = {
        @Index(name = "idx_audit_incident", columnList = "incidentId"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false, length = 60)
    private String actor;

    @Column(nullable = false, updatable = false, length = 60)
    private String action;

    @Column(updatable = false, length = 20)
    private String incidentId;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 20)
    private IncidentStatus prevState;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 20)
    private IncidentStatus newState;

    @Column(nullable = false, updatable = false, length = 1500)
    private String description;

    /** Required by JPA. */
    protected AuditEntry() {
    }

    /** Constructs an immutable AuditEntry instance. */
    public AuditEntry(Instant timestamp, String actor, String action, String incidentId,
                      IncidentStatus prevState, IncidentStatus newState, String description) {
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.actor = actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
        this.action = action == null || action.isBlank() ? "ACTION" : action.trim();
        this.incidentId = incidentId;
        this.prevState = prevState;
        this.newState = newState;
        this.description = description == null ? "" : description.trim();
    }

    public Long getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public IncidentStatus getPrevState() {
        return prevState;
    }

    public IncidentStatus getNewState() {
        return newState;
    }

    public String getDescription() {
        return description;
    }
}
