package com.iitsm.domain.model;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Resolution details composed within an Incident upon resolution. */
@Embeddable
public class Resolution {

    @Column(name = "resolution_summary", length = 1000)
    private String summary;

    @Column(name = "resolution_steps", length = 3000)
    private String steps;

    @Column(name = "resolved_by_code", length = 20)
    private String resolvedByCode;

    @Column(name = "resolved_by_name", length = 120)
    private String resolvedByName;

    /** Required by JPA. */
    protected Resolution() {
    }

    /** Constructs a validated Resolution record for an incident. */
    public Resolution(String summary, String steps, String resolvedByCode, String resolvedByName) {
        if (summary == null || summary.isBlank()) {
            throw new InvalidInputException("Resolution summary cannot be blank.");
        }
        if (steps == null || steps.isBlank()) {
            throw new InvalidInputException("Resolution steps cannot be blank.");
        }
        this.summary = summary.trim();
        this.steps = steps.trim();
        this.resolvedByCode = resolvedByCode == null ? "" : resolvedByCode.trim();
        this.resolvedByName = resolvedByName == null ? "" : resolvedByName.trim();
    }

    public String getSummary() {
        return summary;
    }

    public String getSteps() {
        return steps;
    }

    public String getResolvedByCode() {
        return resolvedByCode;
    }

    public String getResolvedByName() {
        return resolvedByName;
    }
}
