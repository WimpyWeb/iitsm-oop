package com.iitsm;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitsm.config.DataSeeder;
import com.iitsm.controller.dto.DashboardReportDto;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.exception.ApiErrorResponse;
import com.iitsm.exception.GlobalExceptionHandler;
import com.iitsm.exception.InvalidLoginException;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.exception.NotificationDeliveryException;
import com.iitsm.notification.EmailNotifier;
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
import com.iitsm.service.AdminCatalogService;
import com.iitsm.service.IncidentService;
import com.iitsm.service.ReportService;
import com.iitsm.service.SlaEscalationMonitor;
import com.iitsm.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.ModelAndView;

/** End-to-end audit verification suite covering items 0 through 10 of the course audit checklist. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AuditVerificationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserService userService;
    @Autowired private AdminCatalogService catalogService;
    @Autowired private IncidentService incidentService;
    @Autowired private SlaEscalationMonitor slaEscalationMonitor;
    @Autowired private ReportService reportService;
    @Autowired private EmailNotifier emailNotifier;
    @Autowired private GlobalExceptionHandler globalExceptionHandler;

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

    @BeforeEach
    void reseedCleanDatabase() {
        emailNotifier.setMode("simulated");
        incidentService.setSlaTimeScale(1);

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

        dataSeeder.seedIfEmpty();
    }

    @Test
    void seededRolesAllLoginAndSeeDistinctRoleUisAndDataPersistsAcrossReseed() throws Exception {
        assertEquals(30, incidentRepository.count());
        assertEquals(12, userRepository.count());

        // Real form logins for every seeded role: Employee, Technician, TeamLead, SysAdmin, IT Manager
        assertRoleLoginUi("emp1", "/employee/dashboard", "topbar-EMPLOYEE", "Employee Portal");
        assertRoleLoginUi("tech1", "/technician/dashboard", "topbar-TECHNICIAN", "Technician Workbench");
        assertRoleLoginUi("lead1", "/technician/dashboard", "topbar-TECHNICIAN", "Technician Workbench");
        assertRoleLoginUi("sysadmin", "/admin/dashboard", "topbar-ADMIN", "Admin Command Center");
        assertRoleLoginUi("itmanager", "/admin/dashboard", "topbar-ADMIN", "Admin Command Center");

        // Simulating app restart on non-empty H2 DB: DataSeeder must not wipe or duplicate existing records
        dataSeeder.seedIfEmpty();
        assertEquals(30, incidentRepository.count());
        assertEquals(12, userRepository.count());

        // Admin catalog Update CRUD endpoints work cleanly
        Category appCat = categoryRepository.findByNameIgnoreCase("Application").orElseThrow();
        mockMvc.perform(post("/admin/catalog/categories/" + appCat.getId())
                        .with(user("sysadmin").roles("ADMIN")).with(csrf())
                        .param("name", "Application")
                        .param("keywordsCsv", "payment, checkout, portal"))
                .andExpect(status().is3xxRedirection());

        ServiceEntity emailSvc = serviceRepository.findByNameIgnoreCase("Email").orElseThrow();
        mockMvc.perform(post("/admin/catalog/services/" + emailSvc.getId())
                        .with(user("sysadmin").roles("ADMIN")).with(csrf())
                        .param("name", "Email")
                        .param("description", "Corporate SMTP relay and webmail")
                        .param("businessCriticality", "4")
                        .param("keywordsCsv", "email, smtp"))
                .andExpect(status().is3xxRedirection());
        assertEquals(4, serviceRepository.findByNameIgnoreCase("Email").orElseThrow().getBusinessCriticality());
    }

    @Test
    void illegalTransitionsRejectedInBothWebUiAndRestApiWithFriendlyMessage() throws Exception {
        Incident inc1001 = allIncidents().stream()
                .filter(i -> i.getStatus() == IncidentStatus.ASSIGNED)
                .findFirst().orElseThrow();

        // Domain NEW -> CLOSED rejection
        Incident rawNew = new Incident("INC9900",
                (com.iitsm.domain.model.Employee) userService.getByUserCode("E101"),
                serviceRepository.findByNameIgnoreCase("Payment").orElseThrow(),
                "New incident", 3, 3, 10);
        assertThrows(InvalidStateTransitionException.class,
                () -> rawNew.close("Illegal", userService.getByUserCode("T101")));

        // Web UI illegal transition ASSIGNED -> CLOSED returns 409 friendly page without stack traces
        String assigneeUsername = inc1001.getAssignedTechnician().getUsername();
        mockMvc.perform(post("/technician/incidents/" + inc1001.getId() + "/close")
                        .with(user(assigneeUsername).roles("TECHNICIAN")).with(csrf())
                        .param("closureNotes", "Skipping verification"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Illegal incident transition")))
                .andExpect(content().string(not(containsString("at com.iitsm"))));

        // REST API illegal transition ASSIGNED -> CLOSED returns 409 JSON without stack traces
        mockMvc.perform(post("/api/v1/incidents/" + inc1001.getId() + "/transition")
                        .with(httpBasic(assigneeUsername, "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CLOSE\",\"noteOrReason\":\"Skip\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Illegal incident transition")))
                .andExpect(content().string(not(containsString("at com.iitsm"))));
    }

    @Test
    void allTwelveExceptionsTriggeredWithFriendlyMessagesAndNoStackTraces() throws Exception {
        Incident openInc = allIncidents().stream()
                .filter(i -> i.getStatus() == IncidentStatus.ASSIGNED)
                .findFirst().orElseThrow();
        Incident closedInc = allIncidents().stream()
                .filter(i -> i.getStatus() == IncidentStatus.CLOSED)
                .findFirst().orElseThrow();
        ServiceEntity payment = serviceRepository.findByNameIgnoreCase("Payment").orElseThrow();

        // 1. InvalidLoginException
        InvalidLoginException loginEx = assertThrows(InvalidLoginException.class,
                () -> userService.authenticate("emp1", "wrong-pass"));
        MockHttpServletRequest apiReq = new MockHttpServletRequest("POST", "/api/v1/login");
        Object apiRes = globalExceptionHandler.handleItsmException(loginEx, apiReq, new MockHttpServletResponse());
        assertTrue(apiRes instanceof ResponseEntity<?>);
        assertEquals(401, ((ApiErrorResponse) ((ResponseEntity<?>) apiRes).getBody()).status());

        // 2. UnauthorizedException (employee accessing another employee's incident)
        String otherReporter = openInc.getReporter().getUsername().equals("emp1") ? "emp2" : "emp1";
        mockMvc.perform(get("/api/v1/incidents/" + openInc.getId())
                        .with(httpBasic(otherReporter, "Password123!")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("UnauthorizedException"));

        // 3. InvalidIncidentException (editing closed incident)
        mockMvc.perform(post("/employee/incidents/" + closedInc.getId() + "/edit")
                        .with(user(closedInc.getReporter().getUsername()).roles("EMPLOYEE")).with(csrf())
                        .param("description", "Trying to edit closed incident")
                        .param("impact", "3")
                        .param("urgency", "3")
                        .param("affectedUsersCount", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Cannot edit incident")))
                .andExpect(content().string(not(containsString("at com.iitsm"))));

        // 4. InvalidStateTransitionException
        mockMvc.perform(post("/api/v1/incidents/" + openInc.getId() + "/transition")
                        .with(httpBasic(openInc.getAssignedTechnician().getUsername(), "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RESOLVE\",\"summary\":\"Early\",\"steps\":\"Skip\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("InvalidStateTransitionException"));

        // 5. TechnicianUnavailableException
        Technician tech3 = (Technician) userService.getByUserCode("T103");
        tech3.setAvailable(false);
        technicianRepository.save(tech3);
        mockMvc.perform(post("/admin/incidents/" + openInc.getId() + "/assign")
                        .with(user("sysadmin").roles("ADMIN")).with(csrf())
                        .param("technicianCode", "T103"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("unavailable")))
                .andExpect(content().string(not(containsString("at com.iitsm"))));

        // 6. WorkloadExceededException
        tech3.setAvailable(true);
        tech3.setCurrentLoad(tech3.getMaxLoad());
        technicianRepository.save(tech3);
        mockMvc.perform(post("/admin/incidents/" + openInc.getId() + "/assign")
                        .with(user("sysadmin").roles("ADMIN")).with(csrf())
                        .param("technicianCode", "T103"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("max workload")))
                .andExpect(content().string(not(containsString("at com.iitsm"))));

        // 7. ServiceNotFoundException
        mockMvc.perform(post("/api/v1/incidents")
                        .with(httpBasic("emp1", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":999999,\"description\":\"Missing service check\",\"impact\":3,\"urgency\":3,\"affectedUsersCount\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ServiceNotFoundException"));

        // 8. DuplicateIncidentException (same reporter + service + identical description within 10m)
        mockMvc.perform(post("/api/v1/incidents")
                        .with(httpBasic(openInc.getReporter().getUsername(), "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":%d,"description":"%s","impact":5,"urgency":5,"affectedUsersCount":150}
                                """.formatted(openInc.getAffectedService().getId(), openInc.getDescription())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DuplicateIncidentException"));

        // 9. MissingSlaException
        catalogService.deleteSlaPolicy(Priority.P4);
        mockMvc.perform(post("/api/v1/incidents")
                        .with(httpBasic("emp2", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":%d,"description":"Missing SLA policy test","impact":1,"urgency":1,"affectedUsersCount":1}
                                """.formatted(payment.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("MissingSlaException"));

        // 10. PersistenceFailureException (wraps DataAccessException cleanly with no SQL stack trace)
        MockHttpServletRequest webReq = new MockHttpServletRequest("GET", "/employee/dashboard");
        Object webDbErr = globalExceptionHandler.handleDataAccess(
                new DataRetrievalFailureException("simulated SQL failure"), webReq, new MockHttpServletResponse());
        assertTrue(webDbErr instanceof ModelAndView);
        ModelAndView mav = (ModelAndView) webDbErr;
        assertEquals("PersistenceFailureException", mav.getModel().get("errorTitle"));
        assertFalse(mav.getModel().get("errorMessage").toString().contains("simulated SQL failure"));

        // 11. NotificationDeliveryException (network/SMTP failure)
        emailNotifier.setMode("smtp");
        NotificationDeliveryException netEx = assertThrows(NotificationDeliveryException.class,
                () -> emailNotifier.send(
                        new Notification("E101", "INC1001", "EMAIL", "Test", "Body"),
                        userService.getByUserCode("E101")));
        Object netRes = globalExceptionHandler.handleItsmException(netEx, apiReq, new MockHttpServletResponse());
        assertEquals(502, ((ApiErrorResponse) ((ResponseEntity<?>) netRes).getBody()).status());

        // 12. InvalidInputException
        mockMvc.perform(post("/api/v1/incidents/" + openInc.getId() + "/transition")
                        .with(httpBasic("sysadmin", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DOES_NOT_EXIST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("InvalidInputException"));
    }

    @Test
    void securityWrongPasswordXssEscapingInvalidInputAndBcryptHashing() throws Exception {
        // Wrong password rejected in UI and API
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "emp1")
                        .param("password", "WrongPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        mockMvc.perform(get("/api/v1/incidents").with(httpBasic("emp1", "WrongPassword!")))
                .andExpect(status().isUnauthorized());

        // Every stored password in DB must be BCrypt-hashed
        List<User> allUsers = userRepository.findAll();
        assertFalse(allUsers.isEmpty());
        assertTrue(allUsers.stream().allMatch(u ->
                u.getPasswordHash().startsWith("$2a$") || u.getPasswordHash().startsWith("$2b$")));

        // XSS payload is escaped by Thymeleaf when rendered in incident details
        ServiceEntity erp = serviceRepository.findByNameIgnoreCase("ERP").orElseThrow();
        MvcResult created = mockMvc.perform(post("/employee/incidents")
                        .with(user("emp1").roles("EMPLOYEE")).with(csrf())
                        .param("serviceId", erp.getId().toString())
                        .param("description", "<script>alert('xss')</script> ERP dashboard broken")
                        .param("impact", "3")
                        .param("urgency", "3")
                        .param("affectedUsersCount", "10"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String url = created.getResponse().getRedirectedUrl();
        mockMvc.perform(get(url).with(user("emp1").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert")))
                .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))));

        // Invalid input Bean Validation returns 400
        mockMvc.perform(post("/api/v1/incidents")
                        .with(httpBasic("emp1", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":1,\"description\":\"a\",\"impact\":9,\"urgency\":0,\"affectedUsersCount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationFailed"));
    }

    @Test
    void slaDefaultsAndAcceleratedTimeScaleEscalateAllFourLevelsWithinThreeMinutes() {
        SlaPolicy p1 = slaPolicyRepository.findByPriority(Priority.P1).orElseThrow();
        SlaPolicy p2 = slaPolicyRepository.findByPriority(Priority.P2).orElseThrow();
        SlaPolicy p3 = slaPolicyRepository.findByPriority(Priority.P3).orElseThrow();
        SlaPolicy p4 = slaPolicyRepository.findByPriority(Priority.P4).orElseThrow();
        assertEquals(5, p1.getResponseMins());
        assertEquals(30, p1.getResolutionMins());
        assertEquals(15, p2.getResponseMins());
        assertEquals(120, p2.getResolutionMins());
        assertEquals(60, p3.getResponseMins());
        assertEquals(480, p3.getResolutionMins());
        assertEquals(240, p4.getResponseMins());
        assertEquals(1440, p4.getResolutionMins());

        // Time-scale = 10 turns 5m (300s) response deadline into 30s -> all 4 escalation levels in 91s (< 3m)
        incidentService.setSlaTimeScale(10);
        ServiceEntity payment = serviceRepository.findByNameIgnoreCase("Payment").orElseThrow();
        Incident inc = incidentService.createIncident("E102", payment.getId(), null,
                "Accelerated SLA demo outage on checkout nodes", 5, 5, 150);
        assertEquals(30, Duration.between(inc.getCreatedAt(), inc.getSla().getResponseDue()).toSeconds());

        Instant t0 = inc.getCreatedAt();
        // Level 0: 83% elapsed (25s) -> AT_RISK warning
        slaEscalationMonitor.evaluateOpenIncidents(t0.plus(25, ChronoUnit.SECONDS));
        assertEquals(SlaStatus.AT_RISK, incidentRepository.findById(inc.getId()).orElseThrow().getSla().getSlaStatus());

        // Level 1: 31s -> BREACHED + SlaViolation + escalated to TeamLead (T201)
        slaEscalationMonitor.evaluateOpenIncidents(t0.plus(31, ChronoUnit.SECONDS));
        Incident lvl1 = incidentRepository.findById(inc.getId()).orElseThrow();
        assertEquals(SlaStatus.BREACHED, lvl1.getSla().getSlaStatus());
        assertEquals(1, lvl1.getEscalationLevel());
        assertEquals("T201", lvl1.getEscalatedToCode());

        // Level 2: 61s -> escalated to SYSTEM_ADMIN (A001)
        slaEscalationMonitor.evaluateOpenIncidents(t0.plus(61, ChronoUnit.SECONDS));
        assertEquals(2, incidentRepository.findById(inc.getId()).orElseThrow().getEscalationLevel());
        assertEquals("A001", incidentRepository.findById(inc.getId()).orElseThrow().getEscalatedToCode());

        // Level 3: 91s (< 180s = 3 minutes) -> escalated to IT_MANAGER (A002)
        slaEscalationMonitor.evaluateOpenIncidents(t0.plus(91, ChronoUnit.SECONDS));
        Incident lvl3 = incidentRepository.findById(inc.getId()).orElseThrow();
        assertEquals(3, lvl3.getEscalationLevel());
        assertEquals("A002", lvl3.getEscalatedToCode());
    }

    @Test
    void reportCountsMatchDatabaseExactlyOnSeededData() {
        List<Incident> all = allIncidents();
        long expectedTotal = all.size();
        long expectedOpen = all.stream().filter(i -> i.getStatus().isOpen()).count();
        long expectedCritical = all.stream().filter(i -> i.getPriority() == Priority.P1).count();
        long expectedResolved = all.stream().filter(i -> !i.getStatus().isOpen()).count();
        long expectedPayment = all.stream()
                .filter(i -> "Payment".equals(i.getAffectedService().getName())).count();

        DashboardReportDto report = reportService.generateReport();
        assertEquals(expectedTotal, report.totalIncidents());
        assertEquals(expectedOpen, report.openIncidents());
        assertEquals(expectedCritical, report.criticalIncidents());
        assertEquals(expectedResolved, report.resolvedIncidents());
        assertEquals(expectedPayment, report.incidentsByService().get("Payment"));

        for (Technician t : technicianRepository.findAllActiveWithTeam()) {
            String key = t.getUserCode() + " (" + t.getFullName() + ")";
            assertEquals((long) t.getCurrentLoad(), report.technicianWorkload().get(key));
        }
    }

    private void assertRoleLoginUi(String username, String expectedDashboard,
                                   String expectedCssClass, String expectedPortalLabel) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", "Password123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get(expectedDashboard).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(expectedCssClass)))
                .andExpect(content().string(containsString(expectedPortalLabel)));
    }

    private List<Incident> allIncidents() {
        return incidentRepository.findAll(Specification.where(null), Pageable.unpaged()).getContent();
    }
}
