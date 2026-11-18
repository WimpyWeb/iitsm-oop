package com.iitsm.exception;

/** Thrown when a referenced IT service does not exist. */
public class ServiceNotFoundException extends ITSMException {

    /** Constructs a ServiceNotFoundException with a descriptive message. */
    public ServiceNotFoundException(String message) {
        super(message, 404);
    }
}
