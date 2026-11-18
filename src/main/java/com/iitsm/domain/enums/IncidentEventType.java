package com.iitsm.domain.enums;

/** Domain event types published via the Observer pattern for incident lifecycle actions. */
public enum IncidentEventType {
    CREATED,
    CLASSIFIED,
    PRIORITY_CHANGED,
    ASSIGNED,
    ACKNOWLEDGED,
    STATUS_CHANGED,
    INFO_REQUESTED,
    SLA_APPROACHING,
    SLA_VIOLATED,
    ESCALATED,
    RESOLVED,
    CLOSED
}
