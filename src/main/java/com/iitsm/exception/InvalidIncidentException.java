package com.iitsm.exception;

/** Thrown when an incident state or payload is invalid for editing or processing. */
public class InvalidIncidentException extends ITSMException {

    /** Constructs an InvalidIncidentException with a descriptive message. */
    public InvalidIncidentException(String message) {
        super(message, 400);
    }
}
