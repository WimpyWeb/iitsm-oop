package com.iitsm.domain.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.Technician;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.exception.WorkloadExceededException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests covering the incident lifecycle State pattern and transition enforcement. */
class IncidentStateTest {

    private UserFactory userFactory;
    private IncidentFactory incidentFactory;
    private Employee reporter;
    private ServiceEntity paymentService;
    private Category applicationCategory;
    private Technician tech1;
    private Technician tech2;

    @BeforeEach
    void setUp() {
        userFactory = new UserFactory();
        incidentFactory = new IncidentFactory();
        reporter = userFactory.createEmployee("E101", "emp1", "emp1@iitsm.local",
                "Alice Employee", "hash", "555-0101", "Finance", "Analyst");
        paymentService = new ServiceEntity("Payment", "Core payment gateway", 5, List.of("payment", "checkout"));
        applicationCategory = new Category("Application", List.of("payment", "app"));
        tech1 = userFactory.createTechnician("T101", "tech1", "t1@iitsm.local",
                "Bob Tech", "hash", "555-0201", List.of("payment", "java"), 4, 2);
        tech2 = userFactory.createTechnician("T102", "tech2", "t2@iitsm.local",
                "Carol Tech", "hash", "555-0202", List.of("payment", "sql"), 6, 3);
    }

    @Test
    void fullLifecycleFromNewToClosed() {
        Incident incident = incidentFactory.createIncident(
                reporter, paymentService, "Payment application unavailable", 5, 5, 120);
        assertEquals(IncidentStatus.NEW, incident.getStatus());

        incident.classify(applicationCategory, Priority.P1, 187.5);
        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());

        incident.assign(tech1);
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertEquals(1, tech1.getCurrentLoad());

        incident.acknowledge(tech1);
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());
        assertNotNull(incident.getRespondedAt());

        incident.startProgress(tech1);
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());

        incident.requestInfo("Which browser version shows the error?", tech1);
        assertEquals(IncidentStatus.PENDING_INFO, incident.getStatus());
        assertEquals(1, incident.getInfoRequests().size());

        incident.provideInfo(null, "Chrome 129 on Windows 11", reporter);
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());

        incident.resolve("Restarted payment gateway pool", "1. Restarted pool\n2. Verified checkout", tech1);
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertNotNull(incident.getResolvedAt());
        assertEquals(0, tech1.getCurrentLoad());
        assertEquals(1, tech1.getExperienceForService("Payment"));

        incident.verify(reporter);
        assertEquals(IncidentStatus.VERIFIED, incident.getStatus());

        incident.close("Confirmed stable by reporter", tech1);
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
        assertNotNull(incident.getClosure());
    }

    @Test
    void newToClosedThrowsInvalidStateTransition() {
        Incident incident = incidentFactory.createIncident(
                reporter, paymentService, "Payment outage", 4, 4, 50);

        assertThrows(InvalidStateTransitionException.class,
                () -> incident.close("Cannot skip states", tech1));
    }

    @Test
    void resolvedToClosedWithoutVerifyThrowsInvalidStateTransition() {
        Incident incident = advanceToResolved();
        assertThrows(InvalidStateTransitionException.class,
                () -> incident.close("Skip verification", tech1));
    }

    @Test
    void reassignTransfersWorkloadBetweenTechnicians() {
        Incident incident = incidentFactory.createIncident(
                reporter, paymentService, "Payment checkout latency", 3, 4, 15);
        incident.classify(applicationCategory, Priority.P1, 72.0);
        incident.assign(tech1);
        incident.acknowledge(tech1);
        incident.startProgress(tech1);
        assertEquals(1, tech1.getCurrentLoad());
        assertEquals(0, tech2.getCurrentLoad());

        incident.assign(tech2);
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertEquals(tech2, incident.getAssignedTechnician());
        assertEquals(0, tech1.getCurrentLoad());
        assertEquals(1, tech2.getCurrentLoad());
    }

    @Test
    void declineAssignmentReturnsToClassifiedAndRecordsDecliner() {
        Incident incident = incidentFactory.createIncident(
                reporter, paymentService, "Payment failure", 4, 4, 20);
        incident.classify(applicationCategory, Priority.P1, 96.0);
        incident.assign(tech1);

        incident.declineAssignment(tech1, "Out of domain specialty");
        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
        assertNull(incident.getAssignedTechnician());
        assertEquals(0, tech1.getCurrentLoad());
        assertTrue(incident.getDeclinedTechnicianCodes().contains("T101"));
    }

    @Test
    void reopenFromResolvedMovesBackToInProgress() {
        Incident incident = advanceToResolved();
        assertEquals(0, tech1.getCurrentLoad());

        incident.reopen("Issue reoccurred after 5 minutes", reporter);
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getResolvedAt());
        assertEquals(1, tech1.getCurrentLoad());
    }

    @Test
    void assigningOverMaxLoadThrowsWorkloadExceededException() {
        tech1.setCurrentLoad(2);
        Incident incident = incidentFactory.createIncident(
                reporter, paymentService, "Payment timeout", 3, 3, 5);
        incident.classify(applicationCategory, Priority.P2, 45.0);

        assertThrows(WorkloadExceededException.class, () -> incident.assign(tech1));
        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
    }

    private Incident advanceToResolved() {
        Incident incident = incidentFactory.createIncident(
                reporter, paymentService, "Payment gateway crash", 5, 4, 30);
        incident.classify(applicationCategory, Priority.P1, 120.0);
        incident.assign(tech1);
        incident.acknowledge(tech1);
        incident.startProgress(tech1);
        incident.resolve("Cleared thread deadlock", "Restarted worker nodes", tech1);
        return incident;
    }
}
