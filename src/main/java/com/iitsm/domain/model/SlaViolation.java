package com.iitsm.domain.model;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.ViolationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Persisted record of a single RESPONSE or RESOLUTION SLA deadline breach. */
@Entity
@Table(name = "sla_violations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sla_violation_incident_type", columnNames = {"incidentId", "type"})
})
public class SlaViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Column(nullable = false, length = 80)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViolationType type;

    @Column(nullable = false)
    private Instant deadline;

    @Column(nullable = false)
    private Instant detectedAt;

    @Column(length = 20)
    private String assignedTechnicianCode;

    /** Required by JPA. */
    protected SlaViolation() {
    }

    /** Constructs an immutable SLA violation record. */
    public SlaViolation(String incidentId, Priority priority, String serviceName,
                        ViolationType type, Instant deadline, Instant detectedAt,
                        String assignedTechnicianCode) {
        this.incidentId = incidentId;
        this.priority = priority;
        this.serviceName = serviceName;
        this.type = type;
        this.deadline = deadline;
        this.detectedAt = detectedAt == null ? Instant.now() : detectedAt;
        this.assignedTechnicianCode = assignedTechnicianCode;
    }

    public Long getId() {
        return id;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ViolationType getType() {
        return type;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public String getAssignedTechnicianCode() {
        return assignedTechnicianCode;
    }
}
