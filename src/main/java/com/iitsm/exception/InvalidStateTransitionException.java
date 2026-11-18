package com.iitsm.exception;

import com.iitsm.domain.enums.IncidentStatus;

/** Thrown when an incident lifecycle transition violates the State pattern rules. */
public class InvalidStateTransitionException extends ITSMException {

    /** Constructs an InvalidStateTransitionException from source status and attempted action. */
    public InvalidStateTransitionException(IncidentStatus fromStatus, String action) {
        super("Illegal incident transition from " + fromStatus + " via action '" + action + "'", 409);
    }

    /** Constructs an InvalidStateTransitionException with a custom message. */
    public InvalidStateTransitionException(String message) {
        super(message, 409);
    }
}
