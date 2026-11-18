package com.iitsm.domain.observer;

/** Observer pattern contract for reacting to published incident lifecycle events. */
public interface IncidentObserver {

    /** Handles an emitted IncidentEvent synchronously within the event publisher. */
    void onIncidentEvent(IncidentEvent event);
}
