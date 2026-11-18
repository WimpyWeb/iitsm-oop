package com.iitsm.exception;

/** Thrown when an incident ID cannot be found in persistence. */
public class IncidentNotFoundException extends ITSMException {

    /** Constructs an IncidentNotFoundException for the given incident ID. */
    public IncidentNotFoundException(String incidentId) {
        super("Incident not found: " + incidentId, 404);
    }
}
