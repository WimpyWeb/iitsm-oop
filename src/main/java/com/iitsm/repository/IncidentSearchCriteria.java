package com.iitsm.repository;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.SlaStatus;
import java.time.LocalDate;

/** Search filter criteria DTO for combinable JPA Specification incident queries. */
public record IncidentSearchCriteria(
        String incidentId,
        LocalDate fromDate,
        LocalDate toDate,
        IncidentStatus status,
        Priority priority,
        Long categoryId,
        Long serviceId,
        String technicianCode,
        String reporterCode,
        SlaStatus slaStatus,
        String freeText
) {

    /** Empty criteria matching all incidents. */
    public static IncidentSearchCriteria empty() {
        return new IncidentSearchCriteria(null, null, null, null, null, null, null, null, null, null, null);
    }
}
