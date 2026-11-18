package com.iitsm.exception;

/** Thrown when an actor lacks permission or ownership for the requested resource. */
public class UnauthorizedException extends ITSMException {

    /** Constructs an UnauthorizedException with a descriptive message. */
    public UnauthorizedException(String message) {
        super(message, 403);
    }
}
