package com.iitsm.exception;

/** Thrown when no SLA policy or override exists for the incident's priority and service. */
public class MissingSlaException extends ITSMException {

    /** Constructs a MissingSlaException with a descriptive message. */
    public MissingSlaException(String message) {
        super(message, 422);
    }
}
