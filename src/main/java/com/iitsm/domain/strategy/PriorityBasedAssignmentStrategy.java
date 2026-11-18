package com.iitsm.domain.strategy;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Routes P1/P2 incidents to senior specialists while balancing P3/P4 across junior capacity. */
@Component
public class PriorityBasedAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String STRATEGY_NAME = "PRIORITY_BASED";

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public Optional<Technician> selectTechnician(Incident incident, List<Technician> candidates) {
        List<Technician> eligible = filterEligible(incident, candidates);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        Priority priority = incident.getPriority() == null ? Priority.P3 : incident.getPriority();
        if (priority.isAtLeast(Priority.P2)) {
            return eligible.stream()
                    .max(Comparator
                            .comparingInt((Technician t) -> (t instanceof TeamLead ? 4 : 0)
                                    + countSkillMatches(incident, t) * 3
                                    + serviceExperience(incident, t) * 2
                                    + t.getExperienceYears())
                            .thenComparingInt(t -> -t.getCurrentLoad()));
        }
        return eligible.stream()
                .min(Comparator
                        .comparingInt(Technician::getCurrentLoad)
                        .thenComparingInt(Technician::getExperienceYears)
                        .thenComparing(Technician::getUserCode));
    }
}
