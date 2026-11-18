package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.User;
import java.util.EnumSet;
import java.util.Set;

/** State awaiting reporter clarification before returning to IN_PROGRESS. */
public class PendingInfoState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.PENDING_INFO;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.of(IncidentStatus.IN_PROGRESS);
    }

    @Override
    public void provideInfo(Incident incident, Long infoRequestId, String answer, User responder) {
        incident.applyInfoAnswer(infoRequestId, answer, responder, this);
        incident.transitionStatus(IncidentStatus.IN_PROGRESS, this);
    }
}
