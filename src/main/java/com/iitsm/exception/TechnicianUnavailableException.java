package com.iitsm.exception;

/** Thrown when a technician cannot be assigned because they are unavailable or inactive. */
public class TechnicianUnavailableException extends ITSMException {

    /** Constructs a TechnicianUnavailableException with a descriptive message. */
    public TechnicianUnavailableException(String message) {
        super(message, 409);
    }
}
