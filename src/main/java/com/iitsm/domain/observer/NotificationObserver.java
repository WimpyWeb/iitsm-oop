package com.iitsm.domain.observer;

import com.iitsm.domain.enums.IncidentEventType;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.factory.NotificationFactory;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Notification;
import com.iitsm.domain.model.Technician;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Observer generating channel-aware Notifications according to incident event recipient rules. */
@Component
public class NotificationObserver implements IncidentObserver {

    private static final int MAX_BUFFER_SIZE = 500;

    private final NotificationFactory notificationFactory;
    private final Deque<Notification> recentNotifications = new ConcurrentLinkedDeque<>();
    private volatile Consumer<Notification> dispatchSink;
    private volatile Supplier<List<String>> adminCodeSupplier;

    /** Default constructor for standalone domain unit testing. */
    public NotificationObserver() {
        this.notificationFactory = new NotificationFactory();
    }

    /** Spring constructor injecting NotificationFactory. */
    @Autowired
    public NotificationObserver(NotificationFactory notificationFactory) {
        this.notificationFactory = notificationFactory == null ? new NotificationFactory() : notificationFactory;
    }

    /** Registers a callback invoked for each generated Notification (wired by NotificationService). */
    public void setDispatchSink(Consumer<Notification> dispatchSink) {
        this.dispatchSink = dispatchSink;
    }

    /** Registers a supplier providing active admin userCodes when events target admins. */
    public void setAdminCodeSupplier(Supplier<List<String>> adminCodeSupplier) {
        this.adminCodeSupplier = adminCodeSupplier;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        if (event == null || event.incident() == null) {
            return;
        }
        Set<String> recipients = resolveRecipients(event);
        if (recipients.isEmpty()) {
            return;
        }
        List<String> channels = resolveChannels(event);
        Incident incident = event.incident();
        String subject = "[" + incident.getId() + "] " + formatSubject(event.eventType());
        String body = event.description() == null || event.description().isBlank()
                ? subject : event.description();

        for (String recipientCode : recipients) {
            for (String channel : channels) {
                Notification notification = notificationFactory.create(
                        recipientCode, incident.getId(), channel, subject, body);
                recentNotifications.addFirst(notification);
                while (recentNotifications.size() > MAX_BUFFER_SIZE) {
                    recentNotifications.pollLast();
                }
                Consumer<Notification> sink = this.dispatchSink;
                if (sink != null) {
                    sink.accept(notification);
                }
            }
        }
    }

    /** Resolves exact recipient userCodes per Addendum notification policy. */
    public Set<String> resolveRecipients(IncidentEvent event) {
        Incident incident = event.incident();
        Set<String> recipients = new LinkedHashSet<>();
        String reporterCode = incident.getReporter() != null ? incident.getReporter().getUserCode() : null;
        Technician tech = incident.getAssignedTechnician();
        String techCode = tech != null ? tech.getUserCode() : null;
        String leadCode = (tech != null && tech.getTeam() != null && tech.getTeam().getTeamLead() != null)
                ? tech.getTeam().getTeamLead().getUserCode() : null;

        switch (event.eventType()) {
            case CREATED -> {
                addIfPresent(recipients, reporterCode);
                addAdmins(recipients, event.extraRecipientCodes());
            }
            case CLASSIFIED -> {
                // Notify admins when incident enters unassigned queue without an assignee.
                if (techCode == null) {
                    addAdmins(recipients, event.extraRecipientCodes());
                }
            }
            case ASSIGNED, PRIORITY_CHANGED, CLOSED -> {
                addIfPresent(recipients, reporterCode);
                addIfPresent(recipients, techCode);
            }
            case ACKNOWLEDGED, INFO_REQUESTED, RESOLVED -> addIfPresent(recipients, reporterCode);
            case SLA_APPROACHING -> {
                addIfPresent(recipients, techCode);
                Priority p = incident.getPriority();
                if (p != null && p.isAtLeast(Priority.P2)) {
                    addIfPresent(recipients, leadCode);
                    addExtras(recipients, event.extraRecipientCodes());
                }
            }
            case SLA_VIOLATED -> {
                addIfPresent(recipients, techCode);
                addIfPresent(recipients, leadCode);
                addAdmins(recipients, event.extraRecipientCodes());
            }
            case ESCALATED -> {
                addExtras(recipients, event.extraRecipientCodes());
                addIfPresent(recipients, incident.getEscalatedToCode());
            }
            case STATUS_CHANGED -> {
                addIfPresent(recipients, reporterCode);
                addIfPresent(recipients, techCode);
            }
        }
        return recipients;
    }

    /** Determines channels: IN_APP always; EMAIL and SMS for P1/P2 and SLA/escalation events. */
    public List<String> resolveChannels(IncidentEvent event) {
        List<String> channels = new ArrayList<>();
        channels.add("IN_APP");

        Priority priority = event.incident().getPriority();
        boolean highPriority = priority != null && priority.isAtLeast(Priority.P2);
        IncidentEventType type = event.eventType();
        boolean slaOrEscalation = type == IncidentEventType.SLA_APPROACHING
                || type == IncidentEventType.SLA_VIOLATED
                || type == IncidentEventType.ESCALATED;

        if (highPriority || slaOrEscalation) {
            channels.add("EMAIL");
            channels.add("SMS");
        }
        return Collections.unmodifiableList(channels);
    }

    private void addAdmins(Set<String> target, List<String> eventAdmins) {
        addExtras(target, eventAdmins);
        Supplier<List<String>> supplier = this.adminCodeSupplier;
        if (supplier != null) {
            addExtras(target, supplier.get());
        }
    }

    private void addExtras(Set<String> target, List<String> codes) {
        if (codes != null) {
            for (String code : codes) {
                addIfPresent(target, code);
            }
        }
    }

    private void addIfPresent(Set<String> target, String code) {
        if (code != null && !code.isBlank()) {
            target.add(code.trim().toUpperCase());
        }
    }

    private String formatSubject(IncidentEventType type) {
        return switch (type) {
            case CREATED -> "Incident Created";
            case CLASSIFIED -> "Incident Classified";
            case PRIORITY_CHANGED -> "Priority Changed";
            case ASSIGNED -> "Technician Assigned";
            case ACKNOWLEDGED -> "Incident Acknowledged";
            case STATUS_CHANGED -> "Status Updated";
            case INFO_REQUESTED -> "Information Requested";
            case SLA_APPROACHING -> "SLA Approaching Warning";
            case SLA_VIOLATED -> "SLA Violated";
            case ESCALATED -> "Incident Escalated";
            case RESOLVED -> "Incident Resolved";
            case CLOSED -> "Incident Closed";
        };
    }

    /** Returns an unmodifiable snapshot of recently emitted notifications. */
    public List<Notification> getRecentNotifications() {
        return Collections.unmodifiableList(new ArrayList<>(recentNotifications));
    }

    /** Clears the in-memory notification buffer (intended for isolated unit tests). */
    public void clearBuffer() {
        recentNotifications.clear();
    }
}
