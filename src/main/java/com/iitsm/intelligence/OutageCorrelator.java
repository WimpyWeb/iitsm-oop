package com.iitsm.intelligence;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Correlates recent incidents by service (>=3 open in 1h) and upstream dependency outages. */
@Component
public class OutageCorrelator {

    public static final int OUTAGE_THRESHOLD = 3;

    /** Flags potential service outage and upstream dependency root cause on the target incident. */
    public boolean correlate(Incident incident,
                             List<Incident> recentSameServiceIncidents,
                             Set<String> activeOutageServiceNames) {
        if (incident == null || incident.getAffectedService() == null) {
            return false;
        }
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long openInLastHour = 1; // include the current incident itself
        if (recentSameServiceIncidents != null) {
            for (Incident other : recentSameServiceIncidents) {
                if (!other.getId().equals(incident.getId())
                        && other.getStatus().isOpen()
                        && other.getCreatedAt().isAfter(oneHourAgo)) {
                    openInLastHour++;
                }
            }
        }

        boolean outageDetected = openInLastHour >= OUTAGE_THRESHOLD;
        if (outageDetected) {
            incident.setOutageFlagged(true);
            if (recentSameServiceIncidents != null) {
                for (Incident other : recentSameServiceIncidents) {
                    if (other.getStatus().isOpen() && other.getCreatedAt().isAfter(oneHourAgo)) {
                        other.setOutageFlagged(true);
                    }
                }
            }
        }

        ServiceEntity service = incident.getAffectedService();
        if (activeOutageServiceNames != null && service.getDependsOn() != null) {
            for (ServiceEntity upstream : service.getDependsOn()) {
                if (activeOutageServiceNames.contains(upstream.getName())) {
                    incident.setRootCauseSuggestion("Possible root cause: " + upstream.getName());
                    break;
                }
            }
        }
        return outageDetected;
    }
}
