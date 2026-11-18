package com.iitsm.domain.observer;

import com.iitsm.domain.model.AuditEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Observer recording immutable AuditEntry logs for every incident lifecycle event. */
@Component
public class AuditObserver implements IncidentObserver {

    private static final int MAX_BUFFER_SIZE = 500;

    private final Deque<AuditEntry> recentEntries = new ConcurrentLinkedDeque<>();
    private volatile Consumer<AuditEntry> persistenceSink;

    /** Registers a persistence callback used by the service layer to store entries in JPA. */
    public void setPersistenceSink(Consumer<AuditEntry> persistenceSink) {
        this.persistenceSink = persistenceSink;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        if (event == null) {
            return;
        }
        String incidentId = event.incident() == null ? null : event.incident().getId();
        AuditEntry entry = new AuditEntry(
                event.occurredAt(),
                event.actorCode(),
                event.eventType().name(),
                incidentId,
                event.previousStatus(),
                event.newStatus(),
                event.description()
        );
        recentEntries.addFirst(entry);
        while (recentEntries.size() > MAX_BUFFER_SIZE) {
            recentEntries.pollLast();
        }
        Consumer<AuditEntry> sink = this.persistenceSink;
        if (sink != null) {
            sink.accept(entry);
        }
    }

    /** Returns an unmodifiable snapshot of recent audit entries (newest first). */
    public List<AuditEntry> getRecentEntries() {
        return Collections.unmodifiableList(new ArrayList<>(recentEntries));
    }

    /** Clears the in-memory inspection buffer (intended for isolated unit tests). */
    public void clearBuffer() {
        recentEntries.clear();
    }
}
