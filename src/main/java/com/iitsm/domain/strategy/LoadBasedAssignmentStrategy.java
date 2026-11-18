package com.iitsm.domain.strategy;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Assigns the eligible technician with the lowest current workload ratio. */
@Component
public class LoadBasedAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String STRATEGY_NAME = "LOAD_BASED";

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public Optional<Technician> selectTechnician(Incident incident, List<Technician> candidates) {
        return filterEligible(incident, candidates).stream()
                .min(Comparator
                        .comparingDouble((Technician t) -> (double) t.getCurrentLoad() / t.getMaxLoad())
                        .thenComparingInt(Technician::getCurrentLoad)
                        .thenComparingInt(t -> -countSkillMatches(incident, t))
                        .thenComparing(Technician::getUserCode));
    }
}
