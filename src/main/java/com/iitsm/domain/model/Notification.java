package com.iitsm.domain.model;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Persisted notification delivered to a user across in-app, email, or SMS channels. */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipientUserCode,readFlag,createdAt")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String recipientUserCode;

    @Column(length = 20)
    private String incidentId;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false)
    private boolean readFlag = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Required by JPA. */
    protected Notification() {
    }

    /** Constructs a notification targeting a specific userCode and channel. */
    public Notification(String recipientUserCode, String incidentId, String channel, String subject, String message) {
        if (recipientUserCode == null || recipientUserCode.isBlank()) {
            throw new InvalidInputException("Notification recipient userCode cannot be blank.");
        }
        if (subject == null || subject.isBlank()) {
            throw new InvalidInputException("Notification subject cannot be blank.");
        }
        this.recipientUserCode = recipientUserCode.trim().toUpperCase();
        this.incidentId = incidentId;
        this.channel = channel == null ? "IN_APP" : channel.trim().toUpperCase();
        this.subject = subject.trim();
        this.message = message == null ? "" : message.trim();
        this.readFlag = false;
        this.createdAt = Instant.now();
    }

    /** Marks this notification as read. */
    public void markRead() {
        this.readFlag = true;
    }

    public Long getId() {
        return id;
    }

    public String getRecipientUserCode() {
        return recipientUserCode;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public String getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public boolean isReadFlag() {
        return readFlag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
