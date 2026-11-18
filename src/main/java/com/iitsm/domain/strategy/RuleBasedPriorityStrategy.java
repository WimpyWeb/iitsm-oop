package com.iitsm.domain.strategy;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.Incident;
import com.iitsm.exception.InvalidInputException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Rule-based priority strategy applying explicit business and security escalation rules. */
@Component
public class RuleBasedPriorityStrategy implements PriorityStrategy {

    public static final String STRATEGY_NAME = "RULE_BASED";

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public PriorityAssessment calculatePriority(Incident incident) {
        if (incident == null || incident.getAffectedService() == null) {
            throw new InvalidInputException("Incident and affected service are required for rule evaluation.");
        }
        double score = WeightedScorePriorityStrategy.computeRawScore(incident);
        int criticality = incident.getAffectedService().getBusinessCriticality();
        int impact = incident.getImpact();
        int urgency = incident.getUrgency();
        boolean isSecurity = incident.getCategory() != null
                && "security".equalsIgnoreCase(incident.getCategory().getName().toLowerCase(Locale.ROOT));

        Priority priority;
        if ((criticality == 5 && impact >= 4)
                || (isSecurity && impact >= 4)
                || (incident.getAffectedUsersCount() >= 100 && urgency >= 4)) {
            priority = Priority.P1;
        } else if (impact + urgency >= 7 || (criticality >= 4 && (impact >= 3 || urgency >= 3))) {
            priority = Priority.P2;
        } else if (impact >= 3 || urgency >= 3 || criticality >= 3) {
            priority = Priority.P3;
        } else {
            priority = Priority.P4;
        }
        return new PriorityAssessment(priority, score);
    }
}
