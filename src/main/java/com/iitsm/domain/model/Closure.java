package com.iitsm.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** Closure details composed within an Incident once verified and closed. */
@Embeddable
public class Closure {

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_code", length = 20)
    private String closedByCode;

    @Column(name = "closure_notes", length = 1000)
    private String notes;

    /** Required by JPA. */
    protected Closure() {
    }

    /** Constructs a Closure summary. */
    public Closure(Instant closedAt, String closedByCode, String notes) {
        this.closedAt = closedAt == null ? Instant.now() : closedAt;
        this.closedByCode = closedByCode == null ? "SYSTEM" : closedByCode.trim();
        this.notes = notes == null ? "" : notes.trim();
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getClosedByCode() {
        return closedByCode;
    }

    public String getNotes() {
        return notes;
    }
}
