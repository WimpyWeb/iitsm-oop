package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import java.util.Set;

/** State pattern contract defining lifecycle operations and permitted transitions for an Incident. */
public interface IncidentState {

    /** Returns the IncidentStatus enum represented by this state. */
    IncidentStatus getStatus();

    /** Returns the set of valid next statuses reachable from this state. */
    Set<IncidentStatus> getAllowedTransitions();

    /** Classifies a NEW incident with category and computed priority. */
    void classify(Incident incident, Category category, Priority priority, double priorityScore);

    /** Assigns or reassigns an incident to a technician. */
    void assign(Incident incident, Technician technician);

    /** Allows an assigned technician to decline an assignment with a reason. */
    void decline(Incident incident, Technician technician, String reason);

    /** Acknowledges an assigned incident to begin SLA response tracking completion. */
    void acknowledge(Incident incident, Technician actor);

    /** Moves an acknowledged incident into active investigation. */
    void startProgress(Incident incident, Technician actor);

    /** Requests additional information from the reporter, pausing in PENDING_INFO. */
    void requestInfo(Incident incident, String question, User requestedBy);

    /** Answers a pending information request and resumes IN_PROGRESS investigation. */
    void provideInfo(Incident incident, Long infoRequestId, String answer, User responder);

    /** Marks an IN_PROGRESS incident as RESOLVED with summary and steps. */
    void resolve(Incident incident, String summary, String steps, User resolvedBy);

    /** Employee confirms resolution, transitioning RESOLVED to VERIFIED. */
    void verify(Incident incident, User verifier);

    /** Employee rejects resolution and reopens incident back to IN_PROGRESS. */
    void reopen(Incident incident, String reason, User actor);

    /** Closes a VERIFIED incident with final closure notes. */
    void close(Incident incident, String closureNotes, User closedBy);
}
