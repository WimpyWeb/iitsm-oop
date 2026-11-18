package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.EnumSet;
import java.util.Set;

/** State for classified incidents awaiting technician assignment. */
public class ClassifiedState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.CLASSIFIED;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.of(IncidentStatus.ASSIGNED);
    }

    @Override
    public void assign(Incident incident, Technician technician) {
        incident.applyAssignment(technician, this);
        incident.transitionStatus(IncidentStatus.ASSIGNED, this);
    }
}
