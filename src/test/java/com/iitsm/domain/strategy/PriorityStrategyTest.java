package com.iitsm.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for WeightedScorePriorityStrategy, RuleBasedPriorityStrategy, and boundary thresholds. */
class PriorityStrategyTest {

    private WeightedScorePriorityStrategy weightedStrategy;
    private RuleBasedPriorityStrategy ruleStrategy;
    private Employee reporter;
    private IncidentFactory incidentFactory;

    @BeforeEach
    void setUp() {
        weightedStrategy = new WeightedScorePriorityStrategy(60.0, 30.0, 10.0);
        ruleStrategy = new RuleBasedPriorityStrategy();
        reporter = new UserFactory().createEmployee("E101", "emp", "emp@iitsm.local",
                "Alice", "hash", "", "Ops", "Analyst");
        incidentFactory = new IncidentFactory();
    }

    @Test
    void weightedScoreCalculatesExactFormulaAcrossUserTiers() {
        ServiceEntity svc = new ServiceEntity("ERP", "ERP Core", 4, List.of("erp"));

        Incident lowUsers = incidentFactory.createIncident(reporter, svc, "Slow report", 3, 3, 5);
        PriorityAssessment resLow = weightedStrategy.calculatePriority(lowUsers);
        assertEquals(36.0, resLow.score(), 0.001);
        assertEquals(Priority.P2, resLow.priority());

        Incident midUsers = incidentFactory.createIncident(reporter, svc, "Slow report", 3, 3, 50);
        PriorityAssessment resMid = weightedStrategy.calculatePriority(midUsers);
        assertEquals(43.2, resMid.score(), 0.001);
        assertEquals(Priority.P2, resMid.priority());

        Incident highUsers = incidentFactory.createIncident(reporter, svc, "Slow report", 3, 3, 100);
        PriorityAssessment resHigh = weightedStrategy.calculatePriority(highUsers);
        assertEquals(54.0, resHigh.score(), 0.001);
        assertEquals(Priority.P2, resHigh.priority());
    }

    @Test
    void weightedScoreBoundaryThresholdsClassifyCorrectly() {
        ServiceEntity crit5 = new ServiceEntity("Payment", "Payments", 5, List.of("pay"));
        ServiceEntity crit3 = new ServiceEntity("Email", "Mail", 3, List.of("mail"));
        ServiceEntity crit1 = new ServiceEntity("Printer", "Print", 1, List.of("print"));

        // 4 * 3 * 5 * 1.0 = 60.0 -> P1
        assertEquals(Priority.P1, weightedStrategy.calculatePriority(
                incidentFactory.createIncident(reporter, crit5, "Boundary 60", 4, 3, 1)).priority());

        // 5 * 3 * 3 * 1.2 = 54.0 (< 60) -> P2; and custom threshold 59 vs 60 boundary check
        WeightedScorePriorityStrategy tightStrategy = new WeightedScorePriorityStrategy(60.0, 30.0, 10.0);
        Incident score54 = incidentFactory.createIncident(reporter, crit3, "Below 60", 5, 3, 15);
        assertEquals(Priority.P2, tightStrategy.calculatePriority(score54).priority());

        // 2 * 5 * 3 * 1.0 = 30.0 -> P2
        assertEquals(Priority.P2, weightedStrategy.calculatePriority(
                incidentFactory.createIncident(reporter, crit3, "Boundary 30", 2, 5, 1)).priority());

        // 3 * 3 * 3 * 1.0 = 27.0 -> P3
        assertEquals(Priority.P3, weightedStrategy.calculatePriority(
                incidentFactory.createIncident(reporter, crit3, "Score 27", 3, 3, 1)).priority());

        // 2 * 5 * 1 * 1.0 = 10.0 -> P3
        assertEquals(Priority.P3, weightedStrategy.calculatePriority(
                incidentFactory.createIncident(reporter, crit1, "Boundary 10", 2, 5, 1)).priority());

        // 3 * 3 * 1 * 1.0 = 9.0 -> P4
        assertEquals(Priority.P4, weightedStrategy.calculatePriority(
                incidentFactory.createIncident(reporter, crit1, "Score 9", 3, 3, 1)).priority());
    }

    @Test
    void ruleBasedPromotesCriticality5WithImpact4ToP1() {
        ServiceEntity payment = new ServiceEntity("Payment", "Payment", 5, List.of("pay"));
        Incident inc = incidentFactory.createIncident(reporter, payment, "Degraded checkout", 4, 2, 5);
        // Weighted score = 4 * 2 * 5 * 1.0 = 40 (would be P2 in weighted, P1 in rule-based).
        assertEquals(Priority.P1, ruleStrategy.calculatePriority(inc).priority());
    }

    @Test
    void ruleBasedPromotesSecurityCategoryWithImpact4ToP1() {
        ServiceEntity portal = new ServiceEntity("HR Portal", "HR", 2, List.of("hr"));
        Category security = new Category("Security", List.of("breach", "unauthorized"));
        Incident inc = incidentFactory.createIncident(reporter, portal, "Suspicious login token", 4, 2, 2);
        inc.setCategory(security);
        assertEquals(Priority.P1, ruleStrategy.calculatePriority(inc).priority());
    }

    @Test
    void priorityDisplayNamesFollowP1CriticalConvention() {
        assertEquals("P1 Critical", Priority.P1.getDisplayName());
        assertEquals("P2 High", Priority.P2.getDisplayName());
        assertEquals("P3 Medium", Priority.P3.getDisplayName());
        assertEquals("P4 Low", Priority.P4.getDisplayName());
    }
}
