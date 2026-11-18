package com.iitsm.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests verifying all five technician AssignmentStrategy implementations and constraints. */
class AssignmentStrategyTest {

    private Employee reporter;
    private ServiceEntity paymentService;
    private Category dbCategory;
    private IncidentFactory incidentFactory;
    private Technician t101;
    private Technician t102;
    private TeamLead t201;

    @BeforeEach
    void setUp() {
        UserFactory userFactory = new UserFactory();
        incidentFactory = new IncidentFactory();
        reporter = userFactory.createEmployee("E101", "emp", "e@iitsm.local", "Emp", "hash", "", "Ops", "Staff");
        paymentService = new ServiceEntity("Payment", "Payment Gateway", 5, List.of("payment", "checkout"));
        dbCategory = new Category("Database", List.of("database", "sql"));

        t101 = userFactory.createTechnician("T101", "t101", "t1@iitsm.local", "Junior Network",
                "hash", "", List.of("network", "vpn"), 2, 5);
        t101.setCurrentLoad(1);

        t102 = userFactory.createTechnician("T102", "t102", "t2@iitsm.local", "Payment Specialist",
                "hash", "", List.of("payment", "checkout", "sql"), 4, 5);
        t102.setCurrentLoad(3);
        t102.incrementServiceExperience("Payment");
        t102.incrementServiceExperience("Payment");

        t201 = userFactory.createTeamLead("T201", "t201", "lead@iitsm.local", "Senior Lead",
                "hash", "", List.of("payment", "database", "architecture"), 9, 4);
        t201.setCurrentLoad(2);
    }

    @Test
    void skillBasedSelectsTechnicianWithHighestSkillAlignment() {
        Incident inc = buildClassifiedIncident(Priority.P2);
        Optional<Technician> chosen = new SkillBasedAssignmentStrategy()
                .selectTechnician(inc, List.of(t101, t102));
        assertTrue(chosen.isPresent());
        assertEquals("T102", chosen.get().getUserCode());
    }

    @Test
    void loadBasedSelectsLowestWorkloadRatioTechnician() {
        Incident inc = buildClassifiedIncident(Priority.P2);
        Optional<Technician> chosen = new LoadBasedAssignmentStrategy()
                .selectTechnician(inc, List.of(t101, t102, t201));
        assertTrue(chosen.isPresent());
        assertEquals("T101", chosen.get().getUserCode()); // 1/5 = 0.2 < 2/4 = 0.5 < 3/5 = 0.6
    }

    @Test
    void roundRobinCyclesAcrossEligibleTechnicians() {
        RoundRobinAssignmentStrategy rr = new RoundRobinAssignmentStrategy();
        Incident inc = buildClassifiedIncident(Priority.P3);
        List<Technician> pool = List.of(t201, t101, t102);

        assertEquals("T101", rr.selectTechnician(inc, pool).orElseThrow().getUserCode());
        assertEquals("T102", rr.selectTechnician(inc, pool).orElseThrow().getUserCode());
        assertEquals("T201", rr.selectTechnician(inc, pool).orElseThrow().getUserCode());
        assertEquals("T101", rr.selectTechnician(inc, pool).orElseThrow().getUserCode());
    }

    @Test
    void experienceBasedFactorsServiceHistoryAndYears() {
        Incident inc = buildClassifiedIncident(Priority.P2);
        // T102: 2*3 + 4 = 10; T201: 0*3 + 9 = 9 -> T102 wins on service history; increase T201 exp to verify both.
        Optional<Technician> chosen = new ExperienceBasedAssignmentStrategy()
                .selectTechnician(inc, List.of(t101, t102, t201));
        assertEquals("T102", chosen.orElseThrow().getUserCode());
    }

    @Test
    void priorityBasedRoutesP1ToSeniorAndP4ToLowLoadJunior() {
        PriorityBasedAssignmentStrategy strategy = new PriorityBasedAssignmentStrategy();
        Incident critical = buildClassifiedIncident(Priority.P1);
        assertEquals("T201", strategy.selectTechnician(critical, List.of(t101, t102, t201)).orElseThrow().getUserCode());

        Incident low = buildClassifiedIncident(Priority.P4);
        assertEquals("T101", strategy.selectTechnician(low, List.of(t101, t102, t201)).orElseThrow().getUserCode());
    }

    @Test
    void excludesUnavailableFullLoadAndDeclinedTechnicians() {
        t101.setAvailable(false);
        t102.setCurrentLoad(5); // equals maxLoad
        Incident inc = buildClassifiedIncident(Priority.P2);
        inc.assign(t201);
        inc.declineAssignment(t201, "On leave shortly");

        Optional<Technician> chosen = new LoadBasedAssignmentStrategy()
                .selectTechnician(inc, List.of(t101, t102, t201));
        assertTrue(chosen.isEmpty());
    }

    private Incident buildClassifiedIncident(Priority priority) {
        Incident inc = incidentFactory.createIncident(reporter, paymentService, "Payment SQL timeout", 4, 4, 25);
        inc.classify(dbCategory, priority, 96.0);
        return inc;
    }
}
