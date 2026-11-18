package com.iitsm.domain.model;

import com.iitsm.domain.enums.InfoRequestStatus;
import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Question sent by a technician to a reporter awaiting clarification. */
@Entity
@Table(name = "incident_info_requests")
public class InfoRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(length = 2000)
    private String answer;

    @Column(nullable = false, length = 80)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InfoRequestStatus status = InfoRequestStatus.PENDING;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    private Instant answeredAt;

    /** Required by JPA. */
    protected InfoRequest() {
    }

    /** Constructs a pending info request linked to its owning incident. */
    public InfoRequest(Incident incident, String question, String requestedBy) {
        if (incident == null) {
            throw new InvalidInputException("Incident is required for info request.");
        }
        if (question == null || question.isBlank()) {
            throw new InvalidInputException("Question cannot be blank.");
        }
        this.incident = incident;
        this.question = question.trim();
        this.requestedBy = requestedBy == null ? "SYSTEM" : requestedBy.trim();
        this.status = InfoRequestStatus.PENDING;
        this.requestedAt = Instant.now();
    }

    /** Records the reporter's answer and marks the request ANSWERED. */
    public void recordAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            throw new InvalidInputException("Answer cannot be blank.");
        }
        this.answer = answer.trim();
        this.status = InfoRequestStatus.ANSWERED;
        this.answeredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public InfoRequestStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
