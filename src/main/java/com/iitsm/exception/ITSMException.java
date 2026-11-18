package com.iitsm.exception;

/** Base runtime exception for all I-ITSM domain and application errors. */
public abstract class ITSMException extends RuntimeException {

    private final int httpStatus;

    /** Constructs an exception with message and HTTP status code. */
    protected ITSMException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /** Constructs an exception with message, cause, and HTTP status code. */
    protected ITSMException(String message, Throwable cause, int httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** Returns the HTTP status code associated with this error. */
    public int getHttpStatus() {
        return httpStatus;
    }
}
