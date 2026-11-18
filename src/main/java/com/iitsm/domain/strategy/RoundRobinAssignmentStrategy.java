package com.iitsm.domain.strategy;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Distributes assignments cyclically across eligible technicians sorted by userCode. */
@Component
public class RoundRobinAssignmentStrategy extends AbstractAssignmentStrategy {

    public static final String STRATEGY_NAME = "ROUND_ROBIN";

    private final AtomicInteger cursor = new AtomicInteger(0);

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public Optional<Technician> selectTechnician(Incident incident, List<Technician> candidates) {
        List<Technician> eligible = filterEligible(incident, candidates).stream()
                .sorted(Comparator.comparing(Technician::getUserCode))
                .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.floorMod(cursor.getAndIncrement(), eligible.size());
        return Optional.of(eligible.get(index));
    }
}
