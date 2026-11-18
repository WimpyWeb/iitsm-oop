package com.iitsm.exception;

/** Thrown when user or API input violates domain validation constraints. */
public class InvalidInputException extends ITSMException {

    /** Constructs an InvalidInputException with a descriptive message. */
    public InvalidInputException(String message) {
        super(message, 400);
    }
}
