package com.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.model.AuditEntry;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.strategy.LoadBasedAssignmentStrategy;
import com.iitsm.domain.strategy.RuleBasedPriorityStrategy;
import com.iitsm.exception.DuplicateIncidentException;
import com.iitsm.exception.MissingSlaException;
import com.iitsm.exception.UnauthorizedException;
import com.iitsm.notification.EmailNotifier;
import com.iitsm.repository.AuditEntryRepository;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.IncidentSearchCriteria;
import com.iitsm.repository.NotificationRepository;
import com.iitsm.repository.ServiceRepository;
import com.iitsm.repository.SlaPolicyRepository;
import com.iitsm.repository.TeamRepository;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/** Integration tests verifying repositories, services, pipelines, SLA, and notification fallback. */
@SpringBootTest
class IncidentServiceIntegrationTest {

    @Autowired private IncidentService incidentService;
    @Autowired private UserService userService;
    @Autowired private AdminCatalogService catalogService;
    @Autowired private AuditService auditService;
    @Autowired private NotificationService notificationService;
    @Autowired private EmailNotifier emailNotifier;

    @Autowired private IncidentRepository incidentRepository;
    @Autowired private AuditEntryRepository auditEntryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;

    private Employee emp1;
    private Employee emp2;
    private Technician tech1;
    private Technician tech2;
    private ServiceEntity paymentService;
    private Category appCategory;

    @BeforeEach
    void resetDatabase() {
        emailNotifier.setMode("simulated");
        incidentService.setSlaTimeScale(1);

        notificationRepository.deleteAll();
        auditEntryRepository.deleteAll();
        incidentRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
        serviceRepository.deleteAll();
        categoryRepository.deleteAll();
        slaPolicyRepository.deleteAll();

        catalogService.setActivePriorityStrategy("WEIGHTED");
        catalogService.setActiveAssignmentStrategy("SKILL_BASED");

        catalogService.saveOrUpdateSlaPolicy(Priority.P1, 5, 30);
        catalogService.saveOrUpdateSlaPolicy(Priority.P2, 15, 120);
        catalogService.saveOrUpdateSlaPolicy(Priority.P3, 60, 480);
        catalogService.saveOrUpdateSlaPolicy(Priority.P4, 240, 1440);

        appCategory = catalogService.createCategory("Application", List.of("payment", "checkout", "app"));
        catalogService.createCategory("Database", List.of("database", "sql", "query"));
        catalogService.createCategory("Security", List.of("breach", "unauthorized", "token"));

        paymentService = catalogService.createService(
                "Payment", "Payment Gateway", 5, List.of("payment", "checkout", "transaction"),
                null, null, List.of());

        userService.createAdministrator("A001", "sysadmin", "admin@iitsm.local",
                "Sys Admin", "Password123!", "555-0001", AdminLevel.SYSTEM_ADMIN);
        emp1 = userService.registerEmployee("alice", "alice@iitsm.local", "Alice Emp",
                "Password123!", "555-0101", "Finance", "Analyst");
        emp2 = userService.registerEmployee("bob", "bob@iitsm.local", "Bob Emp",
                "Password123!", "555-0102", "Sales", "Rep");
        tech1 = userService.createTechnician("T101", "tech1", "t101@iitsm.local", "Tech One",
                "Password123!", "555-0201", List.of("payment", "checkout"), 5, 2);
        tech2 = userService.createTechnician("T102", "tech2", "t102@iitsm.local", "Tech Two",
                "Password123!", "555-0202", List.of("database", "network"), 3, 2);
    }

    @Test
    void createPipelineRunsSynchronouslyAndAuditsAndNotifiesAllSteps() {
        Incident created = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), null,
                "The payment application is unavailable and users are unable to complete transactions.",
                5, 5, 150);

        assertEquals(IncidentStatus.ASSIGNED, created.getStatus());
        assertEquals(Priority.P1, created.getPriority());
        assertEquals("Application", created.getCategory().getName());
        assertNotNull(created.getAssignedTechnician());
        assertEquals("T101", created.getAssignedTechnician().getUserCode());

        List<AuditEntry> audit = auditService.getTimelineForIncident(created.getId());
        List<String> actions = audit.stream().map(AuditEntry::getAction).toList();
        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNED"), actions);
        assertTrue(audit.get(0).getDescription().contains("Employee " + emp1.getUserCode()));

        assertTrue(notificationService.getUnreadCount(emp1.getUserCode()) >= 1);
        assertTrue(notificationService.getUnreadCount("T101") >= 1);
        assertTrue(notificationService.getUnreadCount("A001") >= 1);
    }

    @Test
    void exactDuplicateThrowsExceptionAndRelatedIncidentsAreLinked() {
        Incident first = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment application unavailable", 5, 4, 50);

        assertThrows(DuplicateIncidentException.class, () ->
                incidentService.createIncident(
                        emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                        "Payment application is unavailable", 5, 4, 50));

        Incident related1 = incidentService.createIncident(
                emp2.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment transaction failing", 4, 4, 30);
        Incident related2 = incidentService.createIncident(
                emp2.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Unable to make payment", 4, 3, 15);

        assertTrue(related1.getRelatedIncidentIds().contains(first.getId()));
        assertTrue(related2.getRelatedIncidentIds().contains(first.getId()));
        assertTrue(related2.getRelatedIncidentIds().contains(related1.getId()));
    }

    @Test
    void unassignedQueueOrdersByPriorityWhenAllTechniciansAtMaxLoad() {
        tech1.setCurrentLoad(2);
        tech2.setCurrentLoad(2);
        technicianRepository.saveAll(List.of(tech1, tech2));

        Incident low = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Minor payment receipt typo", 1, 1, 1);
        Incident critical = incidentService.createIncident(
                emp2.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Complete payment gateway failure", 5, 5, 200);

        assertEquals(IncidentStatus.CLASSIFIED, low.getStatus());
        assertEquals(IncidentStatus.CLASSIFIED, critical.getStatus());

        List<Incident> queue = incidentService.getUnassignedQueue();
        assertEquals(2, queue.size());
        assertEquals(critical.getId(), queue.get(0).getId());
        assertEquals(low.getId(), queue.get(1).getId());
        assertTrue(notificationService.getUnreadCount("A001") >= 2);
    }

    @Test
    void technicianDeclineReassignsExcludingDecliner() {
        Incident inc = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment checkout crashing", 4, 4, 40);
        assertEquals("T101", inc.getAssignedTechnician().getUserCode());

        Incident reassigned = incidentService.declineAssignment(inc.getId(), tech1, "Busy on hardware outage");
        assertEquals(IncidentStatus.ASSIGNED, reassigned.getStatus());
        assertEquals("T102", reassigned.getAssignedTechnician().getUserCode());
        assertTrue(reassigned.getDeclinedTechnicianCodes().contains("T101"));
    }

    @Test
    void serviceSlaOverrideAndMissingSlaAreEnforced() {
        ServiceEntity customSvc = catalogService.createService(
                "ERP", "ERP Core", 3, List.of("erp"), 2, 10, List.of());
        Incident inc = incidentService.createIncident(
                emp1.getUserCode(), customSvc.getId(), appCategory.getId(),
                "ERP invoice glitch", 2, 2, 2);

        long respMinutes = Duration.between(inc.getCreatedAt(), inc.getSla().getResponseDue()).toMinutes();
        long resMinutes = Duration.between(inc.getCreatedAt(), inc.getSla().getResolutionDue()).toMinutes();
        assertEquals(2, respMinutes);
        assertEquals(10, resMinutes);

        catalogService.deleteSlaPolicy(Priority.P3);
        assertThrows(MissingSlaException.class, () ->
                incidentService.createIncident(
                        emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                        "Payment query latency", 2, 2, 1));
    }

    @Test
    void runtimeStrategySwitchUpdatesPriorityAndAssignmentSelection() {
        catalogService.setActivePriorityStrategy(RuleBasedPriorityStrategy.STRATEGY_NAME);
        catalogService.setActiveAssignmentStrategy(LoadBasedAssignmentStrategy.STRATEGY_NAME);

        tech1.setCurrentLoad(1); // load 1/2 = 0.5
        tech2.setCurrentLoad(0); // load 0/2 = 0.0
        technicianRepository.saveAll(List.of(tech1, tech2));

        // Criticality=5 & impact=4 with low urgency=1: weighted score=20 (P3), rule-based=P1!
        Incident inc = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment card validation error", 4, 1, 5);
        assertEquals(Priority.P1, inc.getPriority());
        assertEquals("T102", inc.getAssignedTechnician().getUserCode());
    }

    @Test
    void emailSmtpFailureFallsBackToInAppAndAuditsError() {
        emailNotifier.setMode("smtp"); // No JavaMailSender configured in test -> NotificationDeliveryException

        Incident inc = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Critical payment node failure", 5, 5, 100);

        List<AuditEntry> entries = auditService.getTimelineForIncident(inc.getId());
        assertTrue(entries.stream().anyMatch(e -> "NOTIFICATION_DELIVERY_FAILED".equals(e.getAction())));

        List<Notification> empInApp = notificationRepository
                .findTop20ByRecipientUserCodeIgnoreCaseAndChannelOrderByCreatedAtDesc(
                        emp1.getUserCode(), "IN_APP");
        assertTrue(empInApp.stream().anyMatch(n -> n.getSubject().startsWith("[Fallback Alert]")));
    }

    @Test
    void employeeEditBeforeResolvedRecalculatesPriorityAndOwnershipIsEnforced() {
        Incident inc = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Minor payment button alignment", 1, 2, 2);
        assertEquals(Priority.P3, inc.getPriority());

        assertThrows(UnauthorizedException.class,
                () -> incidentService.getIncidentForActor(inc.getId(), emp2));

        Incident updated = incidentService.updateIncidentDetails(
                inc.getId(), emp1, "Payment checkout broken for everyone", 5, 5, 200);
        assertEquals(Priority.P1, updated.getPriority());

        Page<Incident> searchResult = incidentService.search(
                new IncidentSearchCriteria(null, null, null, IncidentStatus.ASSIGNED, Priority.P1,
                        null, paymentService.getId(), null, emp1.getUserCode(), null, "broken"),
                PageRequest.of(0, 10));
        assertEquals(1, searchResult.getTotalElements());
        assertEquals(inc.getId(), searchResult.getContent().get(0).getId());
    }

    @Test
    void newIncidentPersistsBeforeClassificationWithDefaultSlaInfo() {
        Incident rawNew = new Incident("INC9999", emp1, paymentService,
                "Initial unclassified report", 3, 3, 5);
        Incident saved = incidentRepository.saveAndFlush(rawNew);

        assertEquals(IncidentStatus.NEW, saved.getStatus());
        assertNotNull(saved.getSla());
        assertEquals(SlaStatus.ON_TRACK, saved.getSla().getSlaStatus());
        assertFalse(saved.getSla().isResponseBreachRecorded());
        assertFalse(saved.getSla().isResolutionBreachRecorded());
    }
}
