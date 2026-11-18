package com.iitsm.controller.dto;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.model.Incident;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JSON response DTO representing an Incident without exposing JPA entity proxies. */
public record IncidentResponseDto(
        String id,
        String reporterCode,
        String serviceName,
        String categoryName,
        String description,
        int impact,
        int urgency,
        int affectedUsersCount,
        Priority priority,
        double priorityScore,
        IncidentStatus status,
        SlaStatus slaStatus,
        Instant responseDue,
        Instant resolutionDue,
        String assignedTechnicianCode,
        int escalationLevel,
        boolean outageFlagged,
        String rootCauseSuggestion,
        List<String> relatedIncidentIds,
        Instant createdAt,
        Instant resolvedAt
) {

    /** Maps a domain Incident into an immutable IncidentResponseDto. */
    public static IncidentResponseDto from(Incident inc) {
        return new IncidentResponseDto(
                inc.getId(),
                inc.getReporter() != null ? inc.getReporter().getUserCode() : null,
                inc.getAffectedService() != null ? inc.getAffectedService().getName() : null,
                inc.getCategory() != null ? inc.getCategory().getName() : null,
                inc.getDescription(),
                inc.getImpact(),
                inc.getUrgency(),
                inc.getAffectedUsersCount(),
                inc.getPriority(),
                inc.getPriorityScore(),
                inc.getStatus(),
                inc.getSla() != null ? inc.getSla().getSlaStatus() : null,
                inc.getSla() != null ? inc.getSla().getResponseDue() : null,
                inc.getSla() != null ? inc.getSla().getResolutionDue() : null,
                inc.getAssignedTechnician() != null ? inc.getAssignedTechnician().getUserCode() : null,
                inc.getEscalationLevel(),
                inc.isOutageFlagged(),
                inc.getRootCauseSuggestion(),
                new ArrayList<>(inc.getRelatedIncidentIds()),
                inc.getCreatedAt(),
                inc.getResolvedAt()
        );
    }
}
