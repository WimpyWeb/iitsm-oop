package com.iitsm.domain.strategy;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.List;
import java.util.Optional;

/** Strategy pattern interface for automatically assigning an incident to an eligible technician. */
public interface AssignmentStrategy {

    /** Returns the unique strategy key persisted in AppSetting. */
    String getName();

    /** Selects the best technician for the incident, or empty if none are eligible. */
    Optional<Technician> selectTechnician(Incident incident, List<Technician> candidates);
}
