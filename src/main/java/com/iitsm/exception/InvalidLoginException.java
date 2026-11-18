package com.iitsm.exception;

/** Thrown when authentication credentials are invalid or inactive. */
public class InvalidLoginException extends ITSMException {

    /** Constructs an InvalidLoginException with a descriptive message. */
    public InvalidLoginException(String message) {
        super(message, 401);
    }
}
