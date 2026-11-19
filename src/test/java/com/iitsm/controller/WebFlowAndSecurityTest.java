package com.iitsm.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.Role;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.User;
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
import com.iitsm.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** MockMvc smoke and security tests covering Spring Security, controllers, and Thymeleaf views. */
@SpringBootTest
@AutoConfigureMockMvc
class WebFlowAndSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AdminCatalogService catalogService;

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

    private ServiceEntity paymentService;
    private Category appCategory;

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

        catalogService.saveOrUpdateSlaPolicy(Priority.P1, 5, 30);
        catalogService.saveOrUpdateSlaPolicy(Priority.P2, 15, 120);
        catalogService.saveOrUpdateSlaPolicy(Priority.P3, 60, 480);
        catalogService.saveOrUpdateSlaPolicy(Priority.P4, 240, 1440);

        appCategory = catalogService.createCategory("Application", List.of("payment", "checkout", "app"));
        paymentService = catalogService.createService(
                "Payment", "Core payment gateway", 5, List.of("payment", "checkout"), null, null, List.of());

        userService.createAdministrator("A001", "sysadmin", "admin@iitsm.local",
                "Sys Admin", "Password123!", "555-0001", AdminLevel.SYSTEM_ADMIN);
        userService.registerEmployee("alice", "alice@iitsm.local", "Alice Emp",
                "Password123!", "555-0101", "Finance", "Analyst");
        userService.registerEmployee("bob", "bob@iitsm.local", "Bob Emp",
                "Password123!", "555-0102", "Ops", "Staff");
        userService.createTechnician("T101", "tech1", "t101@iitsm.local", "Tech One",
                "Password123!", "555-0201", List.of("payment", "checkout"), 5, 5);
    }

    @Test
    void smokeTestFullLifecycleThroughWebControllersAndViews() throws Exception {
        // 1. Real form login as employee alice
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "alice")
                        .param("password", "Password123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        MockHttpSession aliceSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/employee/dashboard").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My Reported Incidents")));

        mockMvc.perform(get("/incidents/suggest").session(aliceSession)
                        .param("serviceId", paymentService.getId().toString())
                        .param("description", "Payment application unavailable"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Application")));

        // 2. Report incident -> synchronous NEW -> CLASSIFIED -> ASSIGNED
        MvcResult createResult = mockMvc.perform(post("/employee/incidents").session(aliceSession)
                        .with(csrf())
                        .param("serviceId", paymentService.getId().toString())
                        .param("categoryId", appCategory.getId().toString())
                        .param("description", "The payment application is unavailable for checkout")
                        .param("impact", "5")
                        .param("urgency", "5")
                        .param("affectedUsersCount", "120"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectLocation = createResult.getResponse().getRedirectedUrl();
        assertTrue(redirectLocation != null && redirectLocation.startsWith("/incidents/INC"));
        String incidentId = redirectLocation.substring("/incidents/".length());

        // 3. Technician logs in, acknowledges, starts, requests info, then resolves
        mockMvc.perform(get("/technician/dashboard").with(user("tech1").roles("TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(incidentId)));

        mockMvc.perform(post("/technician/incidents/" + incidentId + "/acknowledge")
                        .with(user("tech1").roles("TECHNICIAN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/technician/incidents/" + incidentId + "/start")
                        .with(user("tech1").roles("TECHNICIAN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/technician/incidents/" + incidentId + "/resolve")
                        .with(user("tech1").roles("TECHNICIAN")).with(csrf())
                        .param("summary", "Restarted payment pool")
                        .param("steps", "1. Flushed connections\n2. Verified checkout"))
                .andExpect(status().is3xxRedirection());

        // 4. Employee verifies & leaves feedback
        mockMvc.perform(post("/employee/incidents/" + incidentId + "/verify")
                        .session(aliceSession).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/employee/incidents/" + incidentId + "/feedback")
                        .session(aliceSession).with(csrf())
                        .param("rating", "5")
                        .param("comment", "Fast resolution!"))
                .andExpect(status().is3xxRedirection());

        // 5. Technician closes verified incident and views audit timeline
        mockMvc.perform(post("/technician/incidents/" + incidentId + "/close")
                        .with(user("tech1").roles("TECHNICIAN")).with(csrf())
                        .param("closureNotes", "Verified stable"))
                .andExpect(status().is3xxRedirection());

        Incident closed = incidentRepository.findById(incidentId).orElseThrow();
        assertEquals(IncidentStatus.CLOSED, closed.getStatus());

        mockMvc.perform(get("/incidents/" + incidentId).with(user("tech1").roles("TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CLOSED")))
                .andExpect(content().string(containsString("Audit Timeline")))
                .andExpect(content().string(containsString("Restarted payment pool")));
    }

    @Test
    void employeeBlockedFromAdminAndOtherEmployeesIncident() throws Exception {
        Incident aliceInc = createSampleIncidentAsAlice();

        mockMvc.perform(get("/admin/dashboard").with(user("alice").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/incidents/" + aliceInc.getId()).with(user("bob").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/employee/incidents/" + aliceInc.getId() + "/edit")
                        .with(user("bob").roles("EMPLOYEE")).with(csrf())
                        .param("description", "Tampered")
                        .param("impact", "1")
                        .param("urgency", "1")
                        .param("affectedUsersCount", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfRequiredOnWebPostsAndSelfRegistrationCreatesEmployeeOnly() throws Exception {
        mockMvc.perform(post("/employee/incidents")
                        .with(user("alice").roles("EMPLOYEE"))
                        .param("serviceId", paymentService.getId().toString())
                        .param("description", "Missing CSRF token test")
                        .param("impact", "3")
                        .param("urgency", "3")
                        .param("affectedUsersCount", "5"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "charlie")
                        .param("email", "charlie@iitsm.local")
                        .param("fullName", "Charlie New")
                        .param("password", "SecretPass123")
                        .param("phone", "555-0199")
                        .param("department", "HR")
                        .param("jobTitle", "Coordinator"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User registered = userRepository.findByUsernameIgnoreCase("charlie").orElseThrow();
        assertEquals(Role.EMPLOYEE, registered.getRole());
    }

    @Test
    void adminViewsSearchAndNotificationBellRenderSuccessfully() throws Exception {
        createSampleIncidentAsAlice();

        mockMvc.perform(get("/admin/dashboard").with(user("sysadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Administrator Operations Console")));

        mockMvc.perform(get("/admin/users").with(user("sysadmin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/teams").with(user("sysadmin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/catalog").with(user("sysadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dependency Graph")));
        mockMvc.perform(get("/admin/audit").with(user("sysadmin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/sla-violations").with(user("sysadmin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/incidents/search").with(user("sysadmin").roles("ADMIN"))
                        .param("freeText", "payment"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Payment")));
        mockMvc.perform(get("/notifications/bell").with(user("alice").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("In-App Alerts")));
    }

    private Incident createSampleIncidentAsAlice() throws Exception {
        MvcResult res = mockMvc.perform(post("/employee/incidents")
                        .with(user("alice").roles("EMPLOYEE")).with(csrf())
                        .param("serviceId", paymentService.getId().toString())
                        .param("categoryId", appCategory.getId().toString())
                        .param("description", "Payment checkout gateway error")
                        .param("impact", "4")
                        .param("urgency", "4")
                        .param("affectedUsersCount", "30"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String url = res.getResponse().getRedirectedUrl();
        String id = url.substring("/incidents/".length());
        return incidentRepository.findById(id).orElseThrow();
    }
}
