package com.iitsm.exception;

/** Thrown when assigning an incident would exceed a technician's maximum workload capacity. */
public class WorkloadExceededException extends ITSMException {

    /** Constructs a WorkloadExceededException with a descriptive message. */
    public WorkloadExceededException(String message) {
        super(message, 409);
    }
}
