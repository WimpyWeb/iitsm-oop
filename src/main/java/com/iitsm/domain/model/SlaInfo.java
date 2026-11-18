package com.iitsm.domain.model;

import com.iitsm.domain.enums.SlaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Duration;
import java.time.Instant;

/** Composed SLA tracking state embedded within an Incident. */
@Embeddable
public class SlaInfo {

    @Column(name = "sla_response_due")
    private Instant responseDue;

    @Column(name = "sla_resolution_due")
    private Instant resolutionDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", length = 20)
    private SlaStatus slaStatus = SlaStatus.ON_TRACK;

    @Column(name = "sla_response_met")
    private Boolean responseMet;

    @Column(name = "sla_resolution_met")
    private Boolean resolutionMet;

    @Column(name = "sla_response_warned")
    private boolean responseApproachingWarned = false;

    @Column(name = "sla_resolution_warned")
    private boolean resolutionApproachingWarned = false;

    @Column(name = "sla_response_breach_recorded")
    private boolean responseBreachRecorded = false;

    @Column(name = "sla_resolution_breach_recorded")
    private boolean resolutionBreachRecorded = false;

    /** Default constructor required by JPA and uninitialized Incident defaults. */
    public SlaInfo() {
    }

    /** Constructs SLA tracking deadlines starting ON_TRACK. */
    public SlaInfo(Instant responseDue, Instant resolutionDue) {
        this.responseDue = responseDue;
        this.resolutionDue = resolutionDue;
        this.slaStatus = SlaStatus.ON_TRACK;
    }

    /** Computes elapsed percentage (0..100+) between start and due timestamps. */
    public static double computeElapsedPercent(Instant start, Instant due, Instant now) {
        if (start == null || due == null || now == null) {
            return 0.0;
        }
        long totalMillis = Duration.between(start, due).toMillis();
        if (totalMillis <= 0) {
            return now.isAfter(due) ? 100.0 : 0.0;
        }
        long elapsedMillis = Duration.between(start, now).toMillis();
        return Math.max(0.0, (elapsedMillis * 100.0) / totalMillis);
    }

    /** Evaluates current SLA status given creation, response, resolution, and current timestamps. */
    public SlaStatus evaluateStatus(Instant createdAt, Instant respondedAt, Instant resolvedAt,
                                    Instant now, int warningPercent) {
        if (resolvedAt != null) {
            boolean resOk = resolutionDue == null || !resolvedAt.isAfter(resolutionDue);
            boolean respOk = responseDue == null || (respondedAt != null && !respondedAt.isAfter(responseDue));
            if (Boolean.FALSE.equals(responseMet) || Boolean.FALSE.equals(resolutionMet) || !resOk || !respOk) {
                this.slaStatus = SlaStatus.BREACHED;
            } else {
                this.slaStatus = SlaStatus.MET;
            }
            return this.slaStatus;
        }

        boolean responseBreached = (respondedAt == null && responseDue != null && now.isAfter(responseDue))
                || Boolean.FALSE.equals(responseMet);
        boolean resolutionBreached = resolutionDue != null && now.isAfter(resolutionDue);
        if (responseBreached || resolutionBreached) {
            this.slaStatus = SlaStatus.BREACHED;
            return this.slaStatus;
        }

        double respPct = (respondedAt == null) ? computeElapsedPercent(createdAt, responseDue, now) : 0.0;
        double resPct = computeElapsedPercent(createdAt, resolutionDue, now);
        if (respPct >= warningPercent || resPct >= warningPercent) {
            this.slaStatus = SlaStatus.AT_RISK;
        } else {
            this.slaStatus = SlaStatus.ON_TRACK;
        }
        return this.slaStatus;
    }

    public Instant getResponseDue() {
        return responseDue;
    }

    public void setResponseDue(Instant responseDue) {
        this.responseDue = responseDue;
    }

    public Instant getResolutionDue() {
        return resolutionDue;
    }

    public void setResolutionDue(Instant resolutionDue) {
        this.resolutionDue = resolutionDue;
    }

    public SlaStatus getSlaStatus() {
        return slaStatus;
    }

    public void setSlaStatus(SlaStatus slaStatus) {
        this.slaStatus = slaStatus;
    }

    public Boolean getResponseMet() {
        return responseMet;
    }

    public void setResponseMet(Boolean responseMet) {
        this.responseMet = responseMet;
    }

    public Boolean getResolutionMet() {
        return resolutionMet;
    }

    public void setResolutionMet(Boolean resolutionMet) {
        this.resolutionMet = resolutionMet;
    }

    public boolean isResponseApproachingWarned() {
        return responseApproachingWarned;
    }

    public void setResponseApproachingWarned(boolean responseApproachingWarned) {
        this.responseApproachingWarned = responseApproachingWarned;
    }

    public boolean isResolutionApproachingWarned() {
        return resolutionApproachingWarned;
    }

    public void setResolutionApproachingWarned(boolean resolutionApproachingWarned) {
        this.resolutionApproachingWarned = resolutionApproachingWarned;
    }

    public boolean isResponseBreachRecorded() {
        return responseBreachRecorded;
    }

    public void setResponseBreachRecorded(boolean responseBreachRecorded) {
        this.responseBreachRecorded = responseBreachRecorded;
    }

    public boolean isResolutionBreachRecorded() {
        return resolutionBreachRecorded;
    }

    public void setResolutionBreachRecorded(boolean resolutionBreachRecorded) {
        this.resolutionBreachRecorded = resolutionBreachRecorded;
    }
}
