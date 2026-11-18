package com.iitsm.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitsm.controller.dto.DashboardReportDto;
import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.enums.ViolationType;
import com.iitsm.domain.model.AuditEntry;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import com.iitsm.intelligence.GeminiAiService;
import com.iitsm.intelligence.LocalTfIdfAiService;
import com.iitsm.repository.AuditEntryRepository;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.NotificationRepository;
import com.iitsm.repository.ServiceRepository;
import com.iitsm.repository.SlaPolicyRepository;
import com.iitsm.repository.SlaViolationRepository;
import com.iitsm.repository.TeamRepository;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Integration tests for SLA monitor, escalation, correlation, AI fallback, reports, and REST API. */
@SpringBootTest
@AutoConfigureMockMvc
class PhaseDIntelligenceSlaAndApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private IncidentService incidentService;
    @Autowired private UserService userService;
    @Autowired private AdminCatalogService catalogService;
    @Autowired private SlaEscalationMonitor slaEscalationMonitor;
    @Autowired private ReportService reportService;
    @Autowired private LocalTfIdfAiService localAiService;

    @Autowired private SlaViolationRepository slaViolationRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditEntryRepository auditEntryRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;

    private Employee emp1;
    private Employee emp2;
    private Technician tech1;
    private TeamLead teamLead;
    private ServiceEntity dbService;
    private ServiceEntity paymentService;
    private Category appCategory;
    private Category dbCategory;

    @BeforeEach
    void setUp() {
        slaViolationRepository.deleteAll();
        notificationRepository.deleteAll();
        auditEntryRepository.deleteAll();
        incidentRepository.deleteAll();
        technicianRepository.findAll().forEach(t -> {
            if (t.getTeam() != null) {
                t.setTeam(null);
                technicianRepository.save(t);
            }
        });
        teamRepository.deleteAll();
        userRepository.deleteAll();
        serviceRepository.deleteAll();
        categoryRepository.deleteAll();
        slaPolicyRepository.deleteAll();

        catalogService.setActivePriorityStrategy("WEIGHTED");
        catalogService.setActiveAssignmentStrategy("SKILL_BASED");
        catalogService.setEscalationMinPriority(Priority.P1);
        incidentService.setSlaTimeScale(1);

        catalogService.saveOrUpdateSlaPolicy(Priority.P1, 5, 30);
        catalogService.saveOrUpdateSlaPolicy(Priority.P2, 15, 120);
        catalogService.saveOrUpdateSlaPolicy(Priority.P3, 60, 480);
        catalogService.saveOrUpdateSlaPolicy(Priority.P4, 240, 1440);

        appCategory = catalogService.createCategory("Application", List.of("payment", "checkout", "api"));
        dbCategory = catalogService.createCategory("Database", List.of("database", "sql", "connection"));

        dbService = catalogService.createService(
                "Database", "Primary SQL cluster", 5, List.of("database", "sql"), null, null, List.of());
        paymentService = catalogService.createService(
                "Payment", "Payment Gateway", 5, List.of("payment", "checkout"),
                null, null, List.of(dbService.getId()));

        userService.createAdministrator("A001", "sysadmin", "sysadmin@iitsm.local",
                "System Admin", "Password123!", "555-0001", AdminLevel.SYSTEM_ADMIN);
        userService.createAdministrator("A002", "itmanager", "manager@iitsm.local",
                "IT Manager", "Password123!", "555-0002", AdminLevel.IT_MANAGER);

        emp1 = userService.registerEmployee("alice", "alice@iitsm.local", "Alice Emp",
                "Password123!", "555-0101", "Finance", "Analyst");
        emp2 = userService.registerEmployee("bob", "bob@iitsm.local", "Bob Emp",
                "Password123!", "555-0102", "Ops", "Staff");

        tech1 = userService.createTechnician("T101", "tech1", "t101@iitsm.local", "Tech One",
                "Password123!", "555-0201", List.of("payment", "checkout"), 4, 8);
        teamLead = userService.createTeamLead("T201", "lead1", "lead1@iitsm.local", "Team Lead",
                "Password123!", "555-0299", List.of("database", "sql"), 8, 5);
        catalogService.createTeam("Core Systems", "Application", teamLead.getUserCode(),
                List.of(tech1.getUserCode()));
    }

    @Test
    void slaApproachingWarningBreachSingleRecordAndMultiLevelEscalation() {
        Incident p1 = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment gateway completely offline", 5, 5, 200);
        Instant created = p1.getCreatedAt();

        // 83% of 5m response window -> AT_RISK + SLA_APPROACHING warning once
        slaEscalationMonitor.evaluateOpenIncidents(created.plus(250, ChronoUnit.SECONDS));
        Incident atRisk = incidentRepository.findById(p1.getId()).orElseThrow();
        assertEquals(SlaStatus.AT_RISK, atRisk.getSla().getSlaStatus());
        assertTrue(atRisk.getSla().isResponseApproachingWarned());
        assertEquals(0, slaViolationRepository.count());

        // Past 5m response deadline -> BREACHED, 1 RESPONSE violation, Escalation Level 1 (TeamLead T201)
        slaEscalationMonitor.evaluateOpenIncidents(created.plus(310, ChronoUnit.SECONDS));
        Incident lvl1 = incidentRepository.findById(p1.getId()).orElseThrow();
        assertEquals(SlaStatus.BREACHED, lvl1.getSla().getSlaStatus());
        assertEquals(1, lvl1.getEscalationLevel());
        assertEquals("T201", lvl1.getEscalatedToCode());
        assertTrue(slaViolationRepository.existsByIncidentIdAndType(p1.getId(), ViolationType.RESPONSE));

        // Re-scan within same interval -> must NOT create duplicate violation rows
        slaEscalationMonitor.evaluateOpenIncidents(created.plus(320, ChronoUnit.SECONDS));
        assertEquals(1, slaViolationRepository.findByIncidentIdOrderByDetectedAtDesc(p1.getId()).size());

        // Further response interval elapsed (10m+) -> Escalation Level 2 (SYSTEM_ADMIN A001)
        slaEscalationMonitor.evaluateOpenIncidents(created.plus(610, ChronoUnit.SECONDS));
        Incident lvl2 = incidentRepository.findById(p1.getId()).orElseThrow();
        assertEquals(2, lvl2.getEscalationLevel());
        assertEquals("A001", lvl2.getEscalatedToCode());

        // Third interval elapsed (15m+) -> Escalation Level 3 (IT_MANAGER A002)
        slaEscalationMonitor.evaluateOpenIncidents(created.plus(910, ChronoUnit.SECONDS));
        Incident lvl3 = incidentRepository.findById(p1.getId()).orElseThrow();
        assertEquals(3, lvl3.getEscalationLevel());
        assertEquals("A002", lvl3.getEscalatedToCode());

        List<AuditEntry> audits = auditEntryRepository.findByIncidentIdOrderByTimestampAsc(p1.getId());
        assertEquals(3, audits.stream().filter(a -> "ESCALATED".equals(a.getAction())).count());
    }

    @Test
    void outageCorrelationAndDependencyRootCauseFlagged() {
        Incident d1 = incidentService.createIncident(
                emp1.getUserCode(), dbService.getId(), dbCategory.getId(),
                "Database connection pool exhausted", 5, 5, 80);
        Incident d2 = incidentService.createIncident(
                emp2.getUserCode(), dbService.getId(), dbCategory.getId(),
                "SQL primary replica read timeout", 5, 4, 60);
        Incident d3 = incidentService.createIncident(
                emp1.getUserCode(), dbService.getId(), dbCategory.getId(),
                "Database transaction deadlock errors", 5, 5, 90);

        assertFalse(d1.isOutageFlagged());
        assertFalse(d2.isOutageFlagged());
        assertTrue(d3.isOutageFlagged());
        assertTrue(incidentRepository.findById(d1.getId()).orElseThrow().isOutageFlagged());

        // Payment depends on Database -> root cause suggestion points to Database!
        Incident pay = incidentService.createIncident(
                emp2.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment checkout hanging on confirmation", 5, 5, 150);
        assertEquals("Possible root cause: Database", pay.getRootCauseSuggestion());
    }

    @Test
    void suggestedResolutionAndGeminiFallbackWorkCleanly() {
        Incident past = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment application unavailable during checkout", 5, 5, 100);
        incidentService.acknowledge(past.getId(), tech1);
        incidentService.startProgress(past.getId(), tech1);
        past = incidentService.resolve(past.getId(), tech1,
                "Flushed payment cache", "1. Clear Redis\n2. Restart pool");

        Incident current = incidentService.createIncident(
                emp2.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment transaction failing at checkout", 4, 4, 50);

        Optional<String> suggestion = incidentService.getSuggestedResolution(current);
        assertTrue(suggestion.isPresent());
        assertTrue(suggestion.get().contains("Flushed payment cache"));

        // Verify GeminiAiService gracefully falls back to LocalTfIdfAiService when unconfigured or failing
        GeminiAiService unconfiguredGemini = new GeminiAiService(localAiService, "", "gemini-2.5-flash");
        assertFalse(unconfiguredGemini.isEnabled());
        assertEquals("Application", unconfiguredGemini.suggestCategory(
                "Payment checkout failure", catalogService.listCategories()).orElseThrow().getName());

        GeminiAiService failingGemini = new GeminiAiService(localAiService, "invalid-key-offline", "gemini-2.5-flash");
        Optional<String> fallbackRes = failingGemini.suggestResolution(current, List.of(past));
        assertTrue(fallbackRes.isPresent());
        assertTrue(fallbackRes.get().contains("Flushed payment cache"));
    }

    @Test
    void reportServiceComputesFeedbackAndRecurringProblems() {
        Incident i1 = incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment gateway timeout during checkout", 5, 5, 100);
        Incident i2 = incidentService.createIncident(
                emp2.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Payment card settlement failing in API", 4, 4, 50);
        incidentService.createIncident(
                emp1.getUserCode(), paymentService.getId(), appCategory.getId(),
                "Refund payment webhook returning 502", 3, 3, 20);

        incidentService.acknowledge(i1.getId(), tech1);
        incidentService.startProgress(i1.getId(), tech1);
        incidentService.resolve(i1.getId(), tech1, "Restarted gateway", "1. Restart");
        incidentService.submitFeedback(i1.getId(), emp1, 5, "Great");

        incidentService.acknowledge(i2.getId(), tech1);
        incidentService.startProgress(i2.getId(), tech1);
        incidentService.resolve(i2.getId(), tech1, "Restarted gateway", "1. Restart");
        incidentService.submitFeedback(i2.getId(), emp2, 3, "Okay");

        DashboardReportDto report = reportService.generateReport();
        assertEquals(3, report.totalIncidents());
        assertEquals(1, report.openIncidents());
        assertEquals(2, report.resolvedIncidents());
        assertEquals(4.0, report.avgFeedbackRating(), 0.01);
        assertEquals(3L, report.recurringServices7d().get("Payment"));
        assertEquals(3L, report.recurringCategories7d().get("Application"));
        assertFalse(report.topRecurringKeywords().isEmpty());
        assertEquals(2L, report.repeatedResolutions().get("Restarted gateway"));
    }

    @Test
    void restApiBasicAuthRoleChecksTransitionsSearchReportsAndSwaggerWork() throws Exception {
        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized());

        String payload = """
                {
                  "serviceId": %d,
                  "description": "Payment application unavailable via API test",
                  "impact": 5,
                  "urgency": 4,
                  "affectedUsersCount": 120
                }
                """.formatted(paymentService.getId());

        MvcResult createdRes = mockMvc.perform(post("/api/v1/incidents")
                        .with(httpBasic("alice", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.priority").value("P1"))
                .andReturn();

        String body = createdRes.getResponse().getContentAsString();
        String incId = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        assertNotNull(incId);

        // Other employee bob cannot access alice's incident via REST
        mockMvc.perform(get("/api/v1/incidents/" + incId)
                        .with(httpBasic("bob", "Password123!")))
                .andExpect(status().isForbidden());

        // Technician transitions ASSIGNED -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED via REST
        mockMvc.perform(post("/api/v1/incidents/" + incId + "/transition")
                        .with(httpBasic("tech1", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mockMvc.perform(post("/api/v1/incidents/" + incId + "/transition")
                        .with(httpBasic("tech1", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/incidents/" + incId + "/transition")
                        .with(httpBasic("tech1", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RESOLVE\",\"summary\":\"Fixed pool\",\"steps\":\"Restarted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Search & Reports endpoints
        mockMvc.perform(get("/api/v1/incidents/search")
                        .with(httpBasic("sysadmin", "Password123!"))
                        .param("freeText", "unavailable"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(incId)));

        mockMvc.perform(get("/api/v1/reports/summary")
                        .with(httpBasic("alice", "Password123!")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reports/summary")
                        .with(httpBasic("sysadmin", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncidents").value(1));

        // Swagger OpenAPI spec and web /reports view
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("I-ITSM REST API")));

        mockMvc.perform(get("/reports")
                        .with(httpBasic("sysadmin", "Password123!")))
                .andExpect(status().is3xxRedirection()); // Web UI uses formLogin

        mockMvc.perform(get("/reports")
                        .with(user("sysadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Live Operational Reports")));
    }
}
