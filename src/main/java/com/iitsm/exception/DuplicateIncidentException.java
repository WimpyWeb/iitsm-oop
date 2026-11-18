package com.iitsm.exception;

/** Thrown when an exact duplicate incident is submitted by the same reporter within 10 minutes. */
public class DuplicateIncidentException extends ITSMException {

    private final String existingIncidentId;

    /** Constructs a DuplicateIncidentException referencing the existing incident ID. */
    public DuplicateIncidentException(String existingIncidentId) {
        super("Duplicate incident detected within 10 minutes: matches " + existingIncidentId, 409);
        this.existingIncidentId = existingIncidentId;
    }

    /** Returns the existing matching incident ID. */
    public String getExistingIncidentId() {
        return existingIncidentId;
    }
}
