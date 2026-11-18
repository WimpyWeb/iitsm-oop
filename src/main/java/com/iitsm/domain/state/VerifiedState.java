package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.User;
import java.util.EnumSet;
import java.util.Set;

/** Employee-verified state permitting technician or admin closure. */
public class VerifiedState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.VERIFIED;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.of(IncidentStatus.CLOSED);
    }

    @Override
    public void close(Incident incident, String closureNotes, User closedBy) {
        incident.applyClosure(closureNotes, closedBy, this);
        incident.transitionStatus(IncidentStatus.CLOSED, this);
    }
}
