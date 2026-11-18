package com.iitsm.domain.model;

import com.iitsm.domain.enums.Priority;
import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Default SLA response and resolution thresholds for a given Priority. */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 10)
    private Priority priority;

    @Column(nullable = false)
    private int responseMins;

    @Column(nullable = false)
    private int resolutionMins;

    /** Required by JPA. */
    protected SlaPolicy() {
    }

    /** Constructs a validated SLA policy for a priority level. */
    public SlaPolicy(Priority priority, int responseMins, int resolutionMins) {
        setPriority(priority);
        setResponseMins(responseMins);
        setResolutionMins(resolutionMins);
    }

    public Long getId() {
        return id;
    }

    public Priority getPriority() {
        return priority;
    }

    public final void setPriority(Priority priority) {
        if (priority == null) {
            throw new InvalidInputException("SLA policy priority cannot be null.");
        }
        this.priority = priority;
    }

    public int getResponseMins() {
        return responseMins;
    }

    public final void setResponseMins(int responseMins) {
        if (responseMins <= 0) {
            throw new InvalidInputException("Response minutes must be positive.");
        }
        this.responseMins = responseMins;
    }

    public int getResolutionMins() {
        return resolutionMins;
    }

    public final void setResolutionMins(int resolutionMins) {
        if (resolutionMins <= 0) {
            throw new InvalidInputException("Resolution minutes must be positive.");
        }
        this.resolutionMins = resolutionMins;
    }
}
