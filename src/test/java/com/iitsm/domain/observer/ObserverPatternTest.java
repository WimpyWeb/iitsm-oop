package com.iitsm.domain.observer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.iitsm.domain.enums.IncidentEventType;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.factory.NotifierFactory;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.AuditEntry;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.Team;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.notification.Notifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for AuditObserver, NotificationObserver, and NotifierFactory. */
class ObserverPatternTest {

    private AuditObserver auditObserver;
    private NotificationObserver notificationObserver;
    private IncidentEventPublisher publisher;
    private Employee reporter;
    private Technician tech;
    private TeamLead teamLead;
    private ServiceEntity paymentService;
    private Category category;
    private IncidentFactory incidentFactory;

    @BeforeEach
    void setUp() {
        auditObserver = new AuditObserver();
        notificationObserver = new NotificationObserver();
        notificationObserver.setAdminCodeSupplier(() -> List.of("A001"));
        publisher = new IncidentEventPublisher(List.of(auditObserver, notificationObserver));

        UserFactory uf = new UserFactory();
        incidentFactory = new IncidentFactory();
        reporter = uf.createEmployee("E101", "emp", "emp@iitsm.local", "Alice", "hash", "", "Ops", "Analyst");
        tech = uf.createTechnician("T101", "t101", "t101@iitsm.local", "Bob", "hash", "", List.of("payment"), 4, 5);
        teamLead = uf.createTeamLead("T201", "t201", "lead@iitsm.local", "Lead", "hash", "", List.of("payment"), 8, 4);
        Team team = new Team("Core Apps", "Application", teamLead);
        team.addMember(tech);

        paymentService = new ServiceEntity("Payment", "Payment Gateway", 5, List.of("payment"));
        category = new Category("Application", List.of("payment"));
    }

    @Test
    void auditObserverCapturesLifecycleEventsWithActorAndStates() {
        Incident inc = incidentFactory.createIncident(reporter, paymentService, "Payment outage", 5, 5, 100);
        publisher.publish(IncidentEvent.of(IncidentEventType.CREATED, inc, null,
                IncidentStatus.NEW, "Employee E101", inc.getId() + " created by Employee E101"));

        inc.classify(category, Priority.P1, 187.5);
        publisher.publish(IncidentEvent.of(IncidentEventType.CLASSIFIED, inc, IncidentStatus.NEW,
                IncidentStatus.CLASSIFIED, "SYSTEM", "Classified as P1 Critical"));

        List<AuditEntry> entries = auditObserver.getRecentEntries();
        assertEquals(2, entries.size());
        assertEquals("CLASSIFIED", entries.get(0).getAction());
        assertEquals(IncidentStatus.NEW, entries.get(0).getPrevState());
        assertEquals("Employee E101", entries.get(1).getActor());
    }

    @Test
    void notificationObserverRoutesP3InAppOnlyAndP1ToMultiChannel() {
        Incident p3 = incidentFactory.createIncident(reporter, paymentService, "Minor label typo", 1, 2, 1);
        p3.classify(category, Priority.P3, 10.0);
        p3.assign(tech);

        publisher.publish(IncidentEvent.of(IncidentEventType.ASSIGNED, p3, IncidentStatus.CLASSIFIED,
                IncidentStatus.ASSIGNED, "SYSTEM", "Assigned T101"));
        List<Notification> p3Notifs = notificationObserver.getRecentNotifications();
        assertEquals(2, p3Notifs.size()); // E101 + T101 IN_APP only
        assertTrue(p3Notifs.stream().allMatch(n -> "IN_APP".equals(n.getChannel())));

        notificationObserver.clearBuffer();
        Incident p1 = incidentFactory.createIncident(reporter, paymentService, "Payment down", 5, 5, 100);
        p1.classify(category, Priority.P1, 187.5);
        p1.assign(tech);

        publisher.publish(IncidentEvent.of(IncidentEventType.SLA_APPROACHING, p1, IncidentStatus.ASSIGNED,
                IncidentStatus.ASSIGNED, "SYSTEM", "SLA approaching 80%"));
        List<Notification> p1Notifs = notificationObserver.getRecentNotifications();
        Set<String> recipients = p1Notifs.stream().map(Notification::getRecipientUserCode).collect(Collectors.toSet());
        Set<String> channels = p1Notifs.stream().map(Notification::getChannel).collect(Collectors.toSet());

        assertEquals(Set.of("T101", "T201"), recipients);
        assertEquals(Set.of("IN_APP", "EMAIL", "SMS"), channels);
    }

    @Test
    void slaViolationNotifiesTechnicianTeamLeadAndAdmins() {
        Incident p3 = incidentFactory.createIncident(reporter, paymentService, "Delayed ticket", 2, 2, 5);
        p3.classify(category, Priority.P3, 20.0);
        p3.assign(tech);

        publisher.publish(IncidentEvent.of(IncidentEventType.SLA_VIOLATED, p3, IncidentStatus.ASSIGNED,
                IncidentStatus.ASSIGNED, "SYSTEM", "Response SLA breached"));

        List<Notification> notifs = notificationObserver.getRecentNotifications();
        Set<String> recipients = notifs.stream().map(Notification::getRecipientUserCode).collect(Collectors.toSet());
        Set<String> channels = notifs.stream().map(Notification::getChannel).collect(Collectors.toSet());

        assertEquals(Set.of("T101", "T201", "A001"), recipients);
        assertEquals(Set.of("IN_APP", "EMAIL", "SMS"), channels);
        assertFalse(recipients.contains("E101"));
    }

    @Test
    void notifierFactoryResolvesRegisteredChannelsCaseInsensitively() {
        List<String> sent = new ArrayList<>();
        Notifier stubInApp = new Notifier() {
            @Override
            public String getChannel() {
                return "IN_APP";
            }

            @Override
            public void send(Notification notification, User recipient) {
                sent.add(notification.getSubject());
            }
        };
        NotifierFactory factory = new NotifierFactory(List.of(stubInApp), null);
        Notification n = factory.createNotification("E101", "INC1001", "in_app", "Test", "Body");
        factory.getNotifier("in_app").send(n, reporter);

        assertTrue(factory.getRegisteredChannels().contains("IN_APP"));
        assertEquals(List.of("Test"), sent);
    }
}
