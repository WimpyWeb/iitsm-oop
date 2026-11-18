package com.iitsm.domain.observer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Custom Observer subject managing registered IncidentObserver listeners and event dispatch. */
@Component
public class IncidentEventPublisher {

    private final CopyOnWriteArrayList<IncidentObserver> observers = new CopyOnWriteArrayList<>();

    /** Default constructor for standalone unit testing. */
    public IncidentEventPublisher() {
    }

    /** Spring constructor automatically registering all IncidentObserver beans. */
    @Autowired
    public IncidentEventPublisher(List<IncidentObserver> initialObservers) {
        if (initialObservers != null) {
            observers.addAll(initialObservers);
        }
    }

    /** Registers an observer if not already present. */
    public void registerObserver(IncidentObserver observer) {
        if (observer != null) {
            observers.addIfAbsent(observer);
        }
    }

    /** Unregisters an observer. */
    public void removeObserver(IncidentObserver observer) {
        observers.remove(observer);
    }

    /** Returns an unmodifiable snapshot of currently registered observers. */
    public List<IncidentObserver> getObservers() {
        return Collections.unmodifiableList(observers);
    }

    /** Publishes an incident event to all registered observers in registration order. */
    public void publish(IncidentEvent event) {
        if (event == null) {
            return;
        }
        for (IncidentObserver observer : observers) {
            observer.onIncidentEvent(event);
        }
    }
}
