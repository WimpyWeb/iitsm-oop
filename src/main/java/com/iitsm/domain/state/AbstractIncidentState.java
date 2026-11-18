package com.iitsm.domain.state;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.exception.InvalidStateTransitionException;

/** Base State implementation rejecting all unsupported actions with InvalidStateTransitionException. */
public abstract class AbstractIncidentState implements IncidentState {

    @Override
    public void classify(Incident incident, Category category, Priority priority, double priorityScore) {
        throw new InvalidStateTransitionException(getStatus(), "classify");
    }

    @Override
    public void assign(Incident incident, Technician technician) {
        throw new InvalidStateTransitionException(getStatus(), "assign");
    }

    @Override
    public void decline(Incident incident, Technician technician, String reason) {
        throw new InvalidStateTransitionException(getStatus(), "decline");
    }

    @Override
    public void acknowledge(Incident incident, Technician actor) {
        throw new InvalidStateTransitionException(getStatus(), "acknowledge");
    }

    @Override
    public void startProgress(Incident incident, Technician actor) {
        throw new InvalidStateTransitionException(getStatus(), "startProgress");
    }

    @Override
    public void requestInfo(Incident incident, String question, User requestedBy) {
        throw new InvalidStateTransitionException(getStatus(), "requestInfo");
    }

    @Override
    public void provideInfo(Incident incident, Long infoRequestId, String answer, User responder) {
        throw new InvalidStateTransitionException(getStatus(), "provideInfo");
    }

    @Override
    public void resolve(Incident incident, String summary, String steps, User resolvedBy) {
        throw new InvalidStateTransitionException(getStatus(), "resolve");
    }

    @Override
    public void verify(Incident incident, User verifier) {
        throw new InvalidStateTransitionException(getStatus(), "verify");
    }

    @Override
    public void reopen(Incident incident, String reason, User actor) {
        throw new InvalidStateTransitionException(getStatus(), "reopen");
    }

    @Override
    public void close(Incident incident, String closureNotes, User closedBy) {
        throw new InvalidStateTransitionException(getStatus(), "close");
    }
}
