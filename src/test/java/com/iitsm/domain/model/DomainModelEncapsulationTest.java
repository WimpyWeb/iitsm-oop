package com.iitsm.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.iitsm.domain.enums.IncidentEventType;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.observer.IncidentEvent;
import com.iitsm.domain.observer.IncidentEventPublisher;
import com.iitsm.exception.InvalidIncidentException;
import com.iitsm.exception.InvalidInputException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests verifying OOP encapsulation, immutability, validation, and Observer dispatch. */
class DomainModelEncapsulationTest {

    @Test
    void auditEntryExposesNoSetters() {
        AuditEntry entry = new AuditEntry(null, "Employee E101", "CREATED", "INC1001",
                null, IncidentStatus.NEW, "Incident created");
        List<String> setterNames = Arrays.stream(AuditEntry.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList();
        assertTrue(setterNames.isEmpty());
        assertEquals("Employee E101", entry.getActor());
    }

    @Test
    void domainCollectionsReturnUnmodifiableViews() {
        UserFactory uf = new UserFactory();
        Technician tech = uf.createTechnician("T101", "t1", "t1@iitsm.local", "Tech",
                "hash", "", List.of("network"), 3, 5);
        Employee emp = uf.createEmployee("E101", "e1", "e1@iitsm.local", "Emp",
                "hash", "", "IT", "Staff");
        ServiceEntity svc = new ServiceEntity("Payment", "Pay", 5, List.of("pay"));
        Incident inc = new IncidentFactory().createIncident(emp, svc, "Pay issue", 3, 3, 5);

        assertThrows(UnsupportedOperationException.class, () -> tech.getSkills().add("hack"));
        assertThrows(UnsupportedOperationException.class, () -> tech.getServiceExperienceCounts().put("Pay", 99));
        assertThrows(UnsupportedOperationException.class, () -> inc.getRelatedIncidentIds().add("INC9999"));
        assertThrows(UnsupportedOperationException.class, () -> svc.getKeywords().add("extra"));
    }

    @Test
    void editingResolvedIncidentThrowsInvalidIncidentException() {
        UserFactory uf = new UserFactory();
        Employee emp = uf.createEmployee("E101", "e1", "e1@iitsm.local", "Emp", "hash", "", "IT", "Staff");
        Technician tech = uf.createTechnician("T101", "t1", "t1@iitsm.local", "Tech", "hash", "", List.of("pay"), 3, 5);
        ServiceEntity svc = new ServiceEntity("Payment", "Pay", 5, List.of("pay"));
        Incident inc = new IncidentFactory().createIncident(emp, svc, "Payment issue", 3, 3, 5);

        inc.updateDetails("Updated payment outage", 4, 4, 20);
        assertEquals("Updated payment outage", inc.getDescription());

        inc.classify(new Category("Application", List.of("pay")), Priority.P1, 96.0);
        inc.assign(tech);
        inc.acknowledge(tech);
        inc.startProgress(tech);
        inc.resolve("Fixed", "Restarted pool", tech);

        assertThrows(InvalidIncidentException.class,
                () -> inc.updateDetails("Late edit", 5, 5, 100));
    }

    @Test
    void serviceCannotDependOnItself() {
        ServiceEntity payment = new ServiceEntity("Payment", "Pay", 5, List.of("pay"));
        assertThrows(InvalidInputException.class, () -> payment.addDependency(payment));
    }

    @Test
    void observerPublisherDispatchesEventsToRegisteredObservers() {
        IncidentEventPublisher publisher = new IncidentEventPublisher();
        List<IncidentEvent> received = new ArrayList<>();
        publisher.registerObserver(received::add);

        Employee emp = new UserFactory().createEmployee("E101", "e1", "e1@iitsm.local", "Emp", "hash", "", "IT", "Staff");
        ServiceEntity svc = new ServiceEntity("Payment", "Pay", 5, List.of("pay"));
        Incident inc = new IncidentFactory().createIncident(emp, svc, "Payment down", 5, 5, 50);

        publisher.publish(IncidentEvent.of(IncidentEventType.CREATED, inc, null,
                IncidentStatus.NEW, "Employee E101", "Created"));
        assertEquals(1, received.size());
        assertEquals(IncidentEventType.CREATED, received.get(0).eventType());
    }
}
