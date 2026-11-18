package com.iitsm.domain.enums;

/** Lifecycle statuses for an incident tracked via the State pattern. */
public enum IncidentStatus {
    NEW,
    CLASSIFIED,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    PENDING_INFO,
    RESOLVED,
    VERIFIED,
    CLOSED;

    /** Returns true if the incident has not yet reached RESOLVED status. */
    public boolean isBeforeResolved() {
        return this.ordinal() < RESOLVED.ordinal();
    }

    /** Returns true if the incident is still active (before RESOLVED/VERIFIED/CLOSED). */
    public boolean isOpen() {
        return isBeforeResolved();
    }

    /** Returns true if the incident is in its terminal CLOSED state. */
    public boolean isClosed() {
        return this == CLOSED;
    }
}
