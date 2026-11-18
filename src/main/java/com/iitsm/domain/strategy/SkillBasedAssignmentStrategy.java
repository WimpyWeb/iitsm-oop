package com.iitsm.domain.strategy;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Assigns the eligible technician with the strongest skill match for the incident domain. */
@Component
public class SkillBasedAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String STRATEGY_NAME = "SKILL_BASED";

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public Optional<Technician> selectTechnician(Incident incident, List<Technician> candidates) {
        return filterEligible(incident, candidates).stream()
                .max(Comparator
                        .comparingInt((Technician t) -> countSkillMatches(incident, t))
                        .thenComparingInt(t -> -t.getCurrentLoad())
                        .thenComparingInt(t -> serviceExperience(incident, t))
                        .thenComparingInt(Technician::getExperienceYears)
                        .thenComparing(Technician::getUserCode, Comparator.reverseOrder()));
    }
}
