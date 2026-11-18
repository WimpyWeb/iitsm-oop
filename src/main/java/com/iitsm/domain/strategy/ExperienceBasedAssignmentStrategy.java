package com.iitsm.domain.strategy;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Prioritizes technicians with the highest service-specific and total experience years. */
@Component
public class ExperienceBasedAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String STRATEGY_NAME = "EXPERIENCE_BASED";

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public Optional<Technician> selectTechnician(Incident incident, List<Technician> candidates) {
        return filterEligible(incident, candidates).stream()
                .max(Comparator
                        .comparingInt((Technician t) -> serviceExperience(incident, t) * 3 + t.getExperienceYears())
                        .thenComparingInt(t -> countSkillMatches(incident, t))
                        .thenComparingInt(t -> -t.getCurrentLoad())
                        .thenComparing(Technician::getUserCode, Comparator.reverseOrder()));
    }
}
