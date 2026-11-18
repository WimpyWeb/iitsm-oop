package com.iitsm.domain.strategy;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.Incident;
import com.iitsm.exception.InvalidInputException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Default weighted priority strategy: score = impact * urgency * criticality * usersFactor. */
@Component
public class WeightedScorePriorityStrategy implements PriorityStrategy {

    public static final String STRATEGY_NAME = "WEIGHTED";

    private final double p1Threshold;
    private final double p2Threshold;
    private final double p3Threshold;

    /** Default constructor with course specification thresholds (60 / 30 / 10). */
    public WeightedScorePriorityStrategy() {
        this(60.0, 30.0, 10.0);
    }

    /** Constructs strategy with externalized threshold properties from application.yml. */
    @Autowired
    public WeightedScorePriorityStrategy(
            @Value("${app.priority.thresholds.p1:60}") double p1Threshold,
            @Value("${app.priority.thresholds.p2:30}") double p2Threshold,
            @Value("${app.priority.thresholds.p3:10}") double p3Threshold) {
        this.p1Threshold = p1Threshold;
        this.p2Threshold = p2Threshold;
        this.p3Threshold = p3Threshold;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public PriorityAssessment calculatePriority(Incident incident) {
        double score = computeRawScore(incident);
        Priority priority;
        if (score >= p1Threshold) {
            priority = Priority.P1;
        } else if (score >= p2Threshold) {
            priority = Priority.P2;
        } else if (score >= p3Threshold) {
            priority = Priority.P3;
        } else {
            priority = Priority.P4;
        }
        return new PriorityAssessment(priority, score);
    }

    /** Computes exact weighted score using impact, urgency, service criticality, and usersFactor. */
    public static double computeRawScore(Incident incident) {
        if (incident == null || incident.getAffectedService() == null) {
            throw new InvalidInputException("Incident and affected service are required for priority scoring.");
        }
        int criticality = incident.getAffectedService().getBusinessCriticality();
        double usersFactor = resolveUsersFactor(incident.getAffectedUsersCount());
        return incident.getImpact() * incident.getUrgency() * criticality * usersFactor;
    }

    /** Resolves affected-user multiplier: 1.0 (<10), 1.2 (10-99), 1.5 (>=100). */
    public static double resolveUsersFactor(int affectedUsersCount) {
        if (affectedUsersCount >= 100) {
            return 1.5;
        }
        if (affectedUsersCount >= 10) {
            return 1.2;
        }
        return 1.0;
    }
}
