package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.EnumSet;
import java.util.Set;

/** State after technician acknowledgement; allows starting investigation, reassignment, or decline. */
public class AcknowledgedState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.ACKNOWLEDGED;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.of(IncidentStatus.IN_PROGRESS, IncidentStatus.ASSIGNED, IncidentStatus.CLASSIFIED);
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
    public void startProgress(Incident incident, Technician actor) {
        incident.transitionStatus(IncidentStatus.IN_PROGRESS, this);
    }
}
