package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.User;
import java.util.EnumSet;
import java.util.Set;

/** Post-resolution state allowing employee verification or reopen back to IN_PROGRESS. */
public class ResolvedState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.RESOLVED;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.of(IncidentStatus.VERIFIED, IncidentStatus.IN_PROGRESS);
    }

    @Override
    public void verify(Incident incident, User verifier) {
        incident.transitionStatus(IncidentStatus.VERIFIED, this);
    }

    @Override
    public void reopen(Incident incident, String reason, User actor) {
        incident.applyReopen(reason, actor, this);
        incident.transitionStatus(IncidentStatus.IN_PROGRESS, this);
    }
}
