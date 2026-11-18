package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.EnumSet;
import java.util.Set;

/** State where a technician is assigned; allows acknowledgement, reassignment, or decline. */
public class AssignedState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.ASSIGNED;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.of(IncidentStatus.ACKNOWLEDGED, IncidentStatus.ASSIGNED, IncidentStatus.CLASSIFIED);
    }

    @Override
    public void assign(Incident incident, Technician technician) {
        incident.applyAssignment(technician, this);
        incident.transitionStatus(IncidentStatus.ASSIGNED, this);
    }

    @Override
    public void decline(Incident incident, Technician technician, String reason) {
        incident.applyDecline(technician, reason, this);
        incident.transitionStatus(IncidentStatus.CLASSIFIED, this);
    }

    @Override
    public void acknowledge(Incident incident, Technician actor) {
        incident.applyAcknowledgement(actor, this);
        incident.transitionStatus(IncidentStatus.ACKNOWLEDGED, this);
    }
}
