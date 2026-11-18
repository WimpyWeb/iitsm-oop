package com.iitsm.domain.observer;

import com.iitsm.domain.enums.IncidentEventType;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import java.time.Instant;
import java.util.List;

/** Immutable domain event emitted whenever an incident undergoes a significant lifecycle action. */
public record IncidentEvent(
        IncidentEventType eventType,
        Incident incident,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String actorCode,
        String description,
        Instant occurredAt,
        List<String> extraRecipientCodes
) {

    /** Creates an IncidentEvent timestamped now with no extra recipient overrides. */
    public static IncidentEvent of(IncidentEventType eventType, Incident incident,
                                   IncidentStatus previousStatus, IncidentStatus newStatus,
                                   String actorCode, String description) {
        return new IncidentEvent(eventType, incident, previousStatus, newStatus,
                actorCode, description, Instant.now(), List.of());
    }

    /** Creates an IncidentEvent targeting explicit additional recipient userCodes. */
    public static IncidentEvent withRecipients(IncidentEventType eventType, Incident incident,
                                               IncidentStatus previousStatus, IncidentStatus newStatus,
                                               String actorCode, String description,
                                               List<String> extraRecipientCodes) {
        return new IncidentEvent(eventType, incident, previousStatus, newStatus,
                actorCode, description, Instant.now(),
                extraRecipientCodes == null ? List.of() : List.copyOf(extraRecipientCodes));
    }
}
