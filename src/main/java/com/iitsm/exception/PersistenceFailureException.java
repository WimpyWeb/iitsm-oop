package com.iitsm.exception;

/** Wraps underlying data access failures into a consistent domain exception. */
public class PersistenceFailureException extends ITSMException {

    /** Constructs a PersistenceFailureException wrapping a persistence layer exception. */
    public PersistenceFailureException(String message, Throwable cause) {
        super(message, cause, 500);
    }
}
