package com.iitsm.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iitsm.config.DataSeeder;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.observer.IncidentEventPublisher;
import com.iitsm.intelligence.AiTextService;
import com.iitsm.intelligence.KeywordClassifier;
import com.iitsm.intelligence.OutageCorrelator;
import com.iitsm.intelligence.TechnicianRecommender;
import com.iitsm.intelligence.TfIdfSimilarityEngine;
import com.iitsm.repository.AuditEntryRepository;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.EmployeeRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.NotificationRepository;
import com.iitsm.repository.ServiceRepository;
import com.iitsm.repository.SlaPolicyRepository;
import com.iitsm.repository.SlaViolationRepository;
import com.iitsm.repository.TeamRepository;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.service.AdminCatalogService;
import com.iitsm.service.AuditService;
import com.iitsm.service.IncidentService;
import com.iitsm.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.ModelAndView;

/** Dedicated tests verifying HTTP status codes and friendly messages for core ITSM exceptions. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ExceptionHandlingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserService userService;
    @Autowired private AdminCatalogService adminCatalogService;
    @Autowired private AuditService auditService;
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

        dataSeeder.seedIfEmpty();
    }

    @Test
    void invalidLoginExceptionReturns401WithFriendlyMessage() {
        InvalidLoginException ex = assertThrows(InvalidLoginException.class,
                () -> userService.authenticate("emp1", "WrongPassword!"));
        assertEquals(401, ex.getHttpStatus());
        assertEquals("Invalid username or password.", ex.getMessage());

        Object apiObj = globalExceptionHandler.handleItsmException(
                ex, new MockHttpServletRequest("POST", "/api/v1/login"), new MockHttpServletResponse());
        ResponseEntity<?> apiRes = assertInstanceOf(ResponseEntity.class, apiObj);
        assertEquals(401, apiRes.getStatusCode().value());
        ApiErrorResponse body = assertInstanceOf(ApiErrorResponse.class, apiRes.getBody());
        assertEquals("InvalidLoginException", body.error());
        assertEquals("Invalid username or password.", body.message());

        Object webObj = globalExceptionHandler.handleItsmException(
                ex, new MockHttpServletRequest("POST", "/login"), new MockHttpServletResponse());
        ModelAndView mav = assertInstanceOf(ModelAndView.class, webObj);
        assertEquals(401, mav.getModel().get("statusCode"));
        assertEquals("Invalid username or password.", mav.getModel().get("errorMessage"));
    }

    @Test
    void incidentNotFoundExceptionReturns404WithFriendlyMessage() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/INC9999")
                        .with(httpBasic("sysadmin", "Password123!")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("IncidentNotFoundException"))
                .andExpect(jsonPath("$.message").value("Incident not found: INC9999"));

        mockMvc.perform(get("/incidents/INC9999")
                        .with(user("sysadmin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Incident not found: INC9999")))
                .andExpect(content().string(not(containsString("at com.iitsm"))));
    }

    @Test
    void serviceNotFoundExceptionReturns404WithFriendlyMessage() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .with(httpBasic("emp1", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": 999999,
                                  "description": "Service missing in catalog",
                                  "impact": 3,
                                  "urgency": 3,
                                  "affectedUsersCount": 5
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ServiceNotFoundException"))
                .andExpect(jsonPath("$.message").value("Service not found: 999999"));
    }

    @Test
    void technicianUnavailableExceptionReturns409WithFriendlyMessage() throws Exception {
        Technician t103 = (Technician) userService.getByUserCode("T103");
        t103.setAvailable(false);
        technicianRepository.save(t103);

        Incident openInc = incidentRepository.findAll(Specification.where(null), Pageable.unpaged())
                .getContent().stream()
                .filter(i -> i.getStatus() == IncidentStatus.ASSIGNED)
                .findFirst().orElseThrow();

        mockMvc.perform(post("/api/v1/incidents/" + openInc.getId() + "/transition")
                        .with(httpBasic("sysadmin", "Password123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\",\"technicianCode\":\"T103\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("TechnicianUnavailableException"))
                .andExpect(jsonPath("$.message").value("Technician T103 is unavailable."));

        mockMvc.perform(post("/admin/incidents/" + openInc.getId() + "/assign")
                        .with(user("sysadmin").roles("ADMIN")).with(csrf())
                        .param("technicianCode", "T103"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Technician T103 is unavailable.")))
                .andExpect(content().string(not(containsString("at com.iitsm"))));
    }

    @Test
    void persistenceFailureExceptionWrapsMockedDataAccessExceptionWith500FriendlyMessage() {
        IncidentRepository mockedIncidentRepo = mock(IncidentRepository.class);
        EmployeeRepository mockedEmpRepo = mock(EmployeeRepository.class);
        ServiceRepository mockedSvcRepo = mock(ServiceRepository.class);
        CategoryRepository mockedCatRepo = mock(CategoryRepository.class);

        Employee reporter = (Employee) userService.getByUserCode("E101");
        ServiceEntity svc = serviceRepository.findByNameIgnoreCase("Payment").orElseThrow();
        Category cat = categoryRepository.findByNameIgnoreCase("Application").orElseThrow();

        when(mockedEmpRepo.findByUserCodeIgnoreCase("E101")).thenReturn(Optional.of(reporter));
        when(mockedSvcRepo.findByIdWithDependencies(anyLong())).thenReturn(Optional.of(svc));
        when(mockedIncidentRepo.findByAffectedServiceIdAndCreatedAtAfterOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(List.of());
        when(mockedCatRepo.findAllByOrderByNameAsc()).thenReturn(List.of(cat));
        when(mockedIncidentRepo.save(any(Incident.class)))
                .thenThrow(new DataRetrievalFailureException("Simulated SQL constraint failure"));

        IncidentService mockedIncidentService = new IncidentService(
                mockedIncidentRepo,
                mockedEmpRepo,
                mock(TechnicianRepository.class),
                mock(UserRepository.class),
                mockedSvcRepo,
                mockedCatRepo,
                mock(SlaPolicyRepository.class),
                new IncidentFactory(),
                new IncidentEventPublisher(),
                adminCatalogService,
                new TfIdfSimilarityEngine(),
                mock(AiTextService.class),
                new KeywordClassifier(),
                new OutageCorrelator(),
                new TechnicianRecommender(),
                auditService,
                1
        );

        PersistenceFailureException ex = assertThrows(PersistenceFailureException.class,
                () -> mockedIncidentService.createIncident(
                        "E101", 1L, null, "Trigger mocked DB failure", 4, 4, 25));
        assertEquals(500, ex.getHttpStatus());
        assertInstanceOf(DataAccessException.class, ex.getCause());
        assertFalse(ex.getMessage().contains("Simulated SQL"));

        Object apiObj = globalExceptionHandler.handleItsmException(
                ex, new MockHttpServletRequest("POST", "/api/v1/incidents"), new MockHttpServletResponse());
        ResponseEntity<?> apiRes = assertInstanceOf(ResponseEntity.class, apiObj);
        assertEquals(500, apiRes.getStatusCode().value());
        ApiErrorResponse body = assertInstanceOf(ApiErrorResponse.class, apiRes.getBody());
        assertEquals("PersistenceFailureException", body.error());

        Object webObj = globalExceptionHandler.handleDataAccess(
                new DataRetrievalFailureException("Raw SQL error"),
                new MockHttpServletRequest("GET", "/employee/dashboard"),
                new MockHttpServletResponse());
        ModelAndView mav = assertInstanceOf(ModelAndView.class, webObj);
        assertEquals(500, mav.getModel().get("statusCode"));
        assertFalse(mav.getModel().get("errorMessage").toString().contains("Raw SQL"));
    }
}
