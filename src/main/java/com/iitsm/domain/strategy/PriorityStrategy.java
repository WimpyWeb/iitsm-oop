package com.iitsm.domain.strategy;

import com.iitsm.domain.model.Incident;

/** Strategy pattern interface for calculating incident priority and numeric urgency score. */
public interface PriorityStrategy {

    /** Returns the unique strategy key persisted in AppSetting. */
    String getName();

    /** Calculates Priority and score for the supplied incident. */
    PriorityAssessment calculatePriority(Incident incident);
}
