package com.iitsm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/** Verifies DataSeeder populates a clean empty database idempotently with all course seed items. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class DataSeederTest {

    @Autowired private DataSeeder dataSeeder;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private AuditEntryRepository auditEntryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SlaViolationRepository slaViolationRepository;

    @BeforeEach
    void cleanDb() {
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
    }

    @Test
    void seedsCleanEmptyDatabaseAndIsIdempotent() {
        dataSeeder.seedIfEmpty();

        assertEquals(9, categoryRepository.count());
        assertEquals(8, serviceRepository.count());
        assertEquals(4, slaPolicyRepository.count());
        assertEquals(12, userRepository.count());
        assertEquals(2, teamRepository.count());
        assertEquals(30, incidentRepository.count());

        ServiceEntity payment = serviceRepository.findByNameIgnoreCase("Payment").orElseThrow();
        ServiceEntity hydratedPayment = serviceRepository.findByIdWithDependencies(payment.getId()).orElseThrow();
        assertFalse(hydratedPayment.getDependsOn().isEmpty());
        assertTrue(hydratedPayment.getDependsOn().stream().anyMatch(d -> "Database".equals(d.getName())));

        Incident inc1001 = incidentRepository.findByIdWithAssociations("INC1001").orElseThrow();
        assertEquals(
                "The payment application is unavailable and users are unable to complete transactions.",
                inc1001.getDescription());

        // Calling seedIfEmpty again must be a safe no-op
        dataSeeder.seedIfEmpty();
        assertEquals(30, incidentRepository.count());
        assertEquals(12, userRepository.count());
    }
}
