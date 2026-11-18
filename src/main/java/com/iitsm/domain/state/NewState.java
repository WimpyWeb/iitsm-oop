package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Incident;
import java.util.EnumSet;
import java.util.Set;

/** Initial state allowing only classification into CLASSIFIED. */
public class NewState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.NEW;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.of(IncidentStatus.CLASSIFIED);
    }

    @Override
    public void classify(Incident incident, Category category, Priority priority, double priorityScore) {
        incident.applyClassification(category, priority, priorityScore, this);
        incident.transitionStatus(IncidentStatus.CLASSIFIED, this);
    }
}
