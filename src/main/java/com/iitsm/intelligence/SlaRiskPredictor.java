package com.iitsm.intelligence;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.SlaInfo;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Predictive SLA analyzer computing elapsed percentages and projected breach risks. */
@Component
public class SlaRiskPredictor {

    /** Snapshot of SLA compliance and predictive breach risk for an incident. */
    public record SlaRiskAssessment(
            SlaStatus status,
            double responseElapsedPercent,
            double resolutionElapsedPercent,
            double maxElapsedPercent,
            boolean projectedBreach
    ) {
    }

    private final int warningThresholdPercent;

    /** Default constructor using 80% warning threshold. */
    public SlaRiskPredictor() {
        this(80);
    }

    /** Spring constructor reading warning threshold from application.yml. */
    @org.springframework.beans.factory.annotation.Autowired
    public SlaRiskPredictor(@Value("${app.sla.warning-threshold-percent:80}") int warningThresholdPercent) {
        this.warningThresholdPercent = warningThresholdPercent;
    }

    /** Assesses current SLA risk and projected breach likelihood at the given instant. */
    public SlaRiskAssessment assess(Incident incident, Instant now) {
        if (incident == null || incident.getSla() == null) {
            return new SlaRiskAssessment(SlaStatus.ON_TRACK, 0.0, 0.0, 0.0, false);
        }
        SlaInfo sla = incident.getSla();
        Instant checkAt = now == null ? Instant.now() : now;
        SlaStatus evaluated = sla.evaluateStatus(
                incident.getCreatedAt(), incident.getRespondedAt(), incident.getResolvedAt(),
                checkAt, warningThresholdPercent);

        double respPct = incident.getRespondedAt() == null
                ? SlaInfo.computeElapsedPercent(incident.getCreatedAt(), sla.getResponseDue(), checkAt)
                : 0.0;
        double resPct = incident.getResolvedAt() == null
                ? SlaInfo.computeElapsedPercent(incident.getCreatedAt(), sla.getResolutionDue(), checkAt)
                : 0.0;
        double maxPct = Math.max(respPct, resPct);

        // High-priority incidents reaching 65% elapsed without completion are projected at breach risk.
        boolean highPriorityEarlyRisk = incident.getPriority() != null
                && incident.getPriority().isAtLeast(Priority.P2)
                && maxPct >= 65.0
                && incident.getResolvedAt() == null;
        boolean projectedBreach = evaluated == SlaStatus.BREACHED
                || evaluated == SlaStatus.AT_RISK
                || highPriorityEarlyRisk;

        return new SlaRiskAssessment(evaluated, respPct, resPct, maxPct, projectedBreach);
    }
}
