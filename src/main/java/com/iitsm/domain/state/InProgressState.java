package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import java.util.EnumSet;
import java.util.Set;

/** Active investigation state allowing info requests, resolution, or reassignment. */
public class InProgressState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.IN_PROGRESS;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.of(IncidentStatus.PENDING_INFO, IncidentStatus.RESOLVED, IncidentStatus.ASSIGNED);
    }

    @Override
    public void assign(Incident incident, Technician technician) {
        incident.applyAssignment(technician, this);
        incident.transitionStatus(IncidentStatus.ASSIGNED, this);
    }

    @Override
    public void requestInfo(Incident incident, String question, User requestedBy) {
        incident.applyInfoRequest(question, requestedBy, this);
        incident.transitionStatus(IncidentStatus.PENDING_INFO, this);
    }

    @Override
    public void resolve(Incident incident, String summary, String steps, User resolvedBy) {
        incident.applyResolution(summary, steps, resolvedBy, this);
        incident.transitionStatus(IncidentStatus.RESOLVED, this);
    }
}
