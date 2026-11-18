package com.iitsm.service;

import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.enums.IncidentEventType;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.enums.ViolationType;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.SlaInfo;
import com.iitsm.domain.model.SlaViolation;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.observer.IncidentEvent;
import com.iitsm.domain.observer.IncidentEventPublisher;
import com.iitsm.intelligence.SlaRiskPredictor;
import com.iitsm.intelligence.SlaRiskPredictor.SlaRiskAssessment;
import com.iitsm.repository.AdministratorRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.SlaViolationRepository;
import com.iitsm.repository.TeamLeadRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scheduled monitor detecting 80% SLA risk, recording breaches once, and escalating P1 incidents. */
@Service
@Transactional
public class SlaEscalationMonitor {

    private static final List<IncidentStatus> OPEN_STATUSES = List.of(
            IncidentStatus.CLASSIFIED,
            IncidentStatus.ASSIGNED,
            IncidentStatus.ACKNOWLEDGED,
            IncidentStatus.IN_PROGRESS,
            IncidentStatus.PENDING_INFO
    );

    private final IncidentRepository incidentRepository;
    private final SlaViolationRepository slaViolationRepository;
    private final TeamLeadRepository teamLeadRepository;
    private final AdministratorRepository administratorRepository;
    private final AdminCatalogService adminCatalogService;
    private final SlaRiskPredictor slaRiskPredictor;
    private final IncidentEventPublisher eventPublisher;
    private final int warningThresholdPercent;

    /** Constructs SlaEscalationMonitor with repositories, risk predictor, and event publisher. */
    public SlaEscalationMonitor(IncidentRepository incidentRepository,
                                SlaViolationRepository slaViolationRepository,
                                TeamLeadRepository teamLeadRepository,
                                AdministratorRepository administratorRepository,
                                AdminCatalogService adminCatalogService,
                                SlaRiskPredictor slaRiskPredictor,
                                IncidentEventPublisher eventPublisher,
                                @Value("${app.sla.warning-threshold-percent:80}") int warningThresholdPercent) {
        this.incidentRepository = incidentRepository;
        this.slaViolationRepository = slaViolationRepository;
        this.teamLeadRepository = teamLeadRepository;
        this.administratorRepository = administratorRepository;
        this.adminCatalogService = adminCatalogService;
        this.slaRiskPredictor = slaRiskPredictor;
        this.eventPublisher = eventPublisher;
        this.warningThresholdPercent = warningThresholdPercent;
    }

    /** Periodic scheduled entrypoint running every configured interval seconds. */
    @Scheduled(fixedDelayString = "#{${app.sla.monitor-interval-seconds:30} * 1000}")
    public void scheduledScan() {
        evaluateOpenIncidents(Instant.now());
    }

    /** Evaluates all active incidents at the supplied timestamp for warnings, breaches, and escalations. */
    public void evaluateOpenIncidents(Instant now) {
        List<Incident> openIncidents = incidentRepository.findByStatusIn(OPEN_STATUSES);
        if (openIncidents.isEmpty()) {
            return;
        }
        Priority minEscalationPriority = adminCatalogService.getEscalationMinPriority();
        Instant evalTime = now == null ? Instant.now() : now;

        for (Incident incident : openIncidents) {
            SlaInfo sla = incident.getSla();
            if (sla == null || sla.getResponseDue() == null || sla.getResolutionDue() == null) {
                continue;
            }
            SlaRiskAssessment risk = slaRiskPredictor.assess(incident, evalTime);
            checkApproachingWarnings(incident, sla, risk, evalTime);
            checkAndRecordViolations(incident, sla, evalTime);
            checkEscalation(incident, sla, minEscalationPriority, evalTime);

            // Re-evaluate final SLA status cleanly after updates.
            sla.evaluateStatus(incident.getCreatedAt(), incident.getRespondedAt(),
                    incident.getResolvedAt(), evalTime, warningThresholdPercent);
            incidentRepository.save(incident);
        }
    }

    private void checkApproachingWarnings(Incident incident, SlaInfo sla, SlaRiskAssessment risk, Instant now) {
        if (incident.getRespondedAt() == null
                && !sla.isResponseApproachingWarned()
                && !now.isAfter(sla.getResponseDue())
                && risk.responseElapsedPercent() >= warningThresholdPercent) {
            sla.setResponseApproachingWarned(true);
            sla.setSlaStatus(SlaStatus.AT_RISK);
            eventPublisher.publish(IncidentEvent.of(
                    IncidentEventType.SLA_APPROACHING, incident, incident.getStatus(), incident.getStatus(),
                    "SYSTEM", String.format("%s response SLA at %.0f%% elapsed",
                            incident.getId(), risk.responseElapsedPercent())));
        }

        if (incident.getResolvedAt() == null
                && !sla.isResolutionApproachingWarned()
                && !now.isAfter(sla.getResolutionDue())
                && risk.resolutionElapsedPercent() >= warningThresholdPercent) {
            sla.setResolutionApproachingWarned(true);
            if (sla.getSlaStatus() != SlaStatus.BREACHED) {
                sla.setSlaStatus(SlaStatus.AT_RISK);
            }
            eventPublisher.publish(IncidentEvent.of(
                    IncidentEventType.SLA_APPROACHING, incident, incident.getStatus(), incident.getStatus(),
                    "SYSTEM", String.format("%s resolution SLA at %.0f%% elapsed",
                            incident.getId(), risk.resolutionElapsedPercent())));
        }
    }

    private void checkAndRecordViolations(Incident incident, SlaInfo sla, Instant now) {
        String techCode = incident.getAssignedTechnician() != null
                ? incident.getAssignedTechnician().getUserCode() : null;
        String serviceName = incident.getAffectedService() != null
                ? incident.getAffectedService().getName() : "Unknown";

        if (incident.getRespondedAt() == null && now.isAfter(sla.getResponseDue())) {
            sla.setResponseMet(false);
            if (!sla.isResponseBreachRecorded()
                    && !slaViolationRepository.existsByIncidentIdAndType(incident.getId(), ViolationType.RESPONSE)) {
                slaViolationRepository.save(new SlaViolation(
                        incident.getId(), incident.getPriority(), serviceName,
                        ViolationType.RESPONSE, sla.getResponseDue(), now, techCode));
                sla.setResponseBreachRecorded(true);
                eventPublisher.publish(IncidentEvent.of(
                        IncidentEventType.SLA_VIOLATED, incident, incident.getStatus(), incident.getStatus(),
                        "SYSTEM", incident.getId() + " violated RESPONSE SLA deadline"));
            }
        }

        if (incident.getResolvedAt() == null && now.isAfter(sla.getResolutionDue())) {
            sla.setResolutionMet(false);
            if (!sla.isResolutionBreachRecorded()
                    && !slaViolationRepository.existsByIncidentIdAndType(incident.getId(), ViolationType.RESOLUTION)) {
                slaViolationRepository.save(new SlaViolation(
                        incident.getId(), incident.getPriority(), serviceName,
                        ViolationType.RESOLUTION, sla.getResolutionDue(), now, techCode));
                sla.setResolutionBreachRecorded(true);
                eventPublisher.publish(IncidentEvent.of(
                        IncidentEventType.SLA_VIOLATED, incident, incident.getStatus(), incident.getStatus(),
                        "SYSTEM", incident.getId() + " violated RESOLUTION SLA deadline"));
            }
        }
    }

    private void checkEscalation(Incident incident, SlaInfo sla, Priority minPriority, Instant now) {
        if (incident.getRespondedAt() != null
                || incident.getPriority() == null
                || !incident.getPriority().isAtLeast(minPriority)
                || !now.isAfter(sla.getResponseDue())) {
            return;
        }
        long windowMillis = Math.max(1000L, Duration.between(incident.getCreatedAt(), sla.getResponseDue()).toMillis());
        long elapsedMillis = Duration.between(incident.getCreatedAt(), now).toMillis();
        int targetLevel = (int) Math.min(3L, Math.max(1L, elapsedMillis / windowMillis));

        while (incident.getEscalationLevel() < targetLevel) {
            int nextLevel = incident.getEscalationLevel() + 1;
            List<String> newOwners = resolveEscalationOwners(incident, nextLevel);
            String primaryOwner = newOwners.isEmpty() ? "ADMIN" : newOwners.get(0);

            incident.setEscalationLevel(nextLevel);
            incident.setEscalatedToCode(primaryOwner);
            incident.setLastEscalatedAt(now);

            String levelLabel = switch (nextLevel) {
                case 1 -> "Level 1 (TeamLead)";
                case 2 -> "Level 2 (SYSTEM_ADMIN)";
                default -> "Level 3 (IT_MANAGER)";
            };
            eventPublisher.publish(IncidentEvent.withRecipients(
                    IncidentEventType.ESCALATED, incident, incident.getStatus(), incident.getStatus(),
                    "SYSTEM", incident.getId() + " escalated to " + levelLabel + " -> " + primaryOwner,
                    newOwners));
        }
    }

    private List<String> resolveEscalationOwners(Incident incident, int level) {
        if (level == 1) {
            if (incident.getAssignedTechnician() != null
                    && incident.getAssignedTechnician().getTeam() != null
                    && incident.getAssignedTechnician().getTeam().getTeamLead() != null) {
                return List.of(incident.getAssignedTechnician().getTeam().getTeamLead().getUserCode());
            }
            return teamLeadRepository.findByActiveTrueOrderByUserCodeAsc().stream()
                    .map(TeamLead::getUserCode)
                    .limit(1)
                    .toList();
        }
        if (level == 2) {
            return administratorRepository.findByAdminLevelAndActiveTrueOrderByUserCodeAsc(AdminLevel.SYSTEM_ADMIN)
                    .stream().map(Administrator::getUserCode).toList();
        }
        List<String> managers = administratorRepository
                .findByAdminLevelAndActiveTrueOrderByUserCodeAsc(AdminLevel.IT_MANAGER)
                .stream().map(Administrator::getUserCode).toList();
        if (!managers.isEmpty()) {
            return managers;
        }
        return administratorRepository.findByActiveTrueOrderByUserCodeAsc()
                .stream().map(Administrator::getUserCode).toList();
    }
}
