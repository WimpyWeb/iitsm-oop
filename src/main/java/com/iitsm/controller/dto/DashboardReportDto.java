package com.iitsm.controller.dto;

import java.util.List;
import java.util.Map;

/** Aggregated analytics and recurring problems report DTO shared by web views and REST API. */
public record DashboardReportDto(
        long totalIncidents,
        long openIncidents,
        long criticalIncidents,
        long resolvedIncidents,
        long slaViolationsCount,
        double slaCompliancePercent,
        double avgResolutionMinutes,
        double avgFeedbackRating,
        Map<String, Long> incidentsByPriority,
        Map<String, Long> incidentsByStatus,
        Map<String, Long> incidentsByService,
        Map<String, Long> technicianWorkload,
        Map<String, Long> recurringServices7d,
        Map<String, Long> recurringCategories7d,
        List<String> topRecurringKeywords,
        Map<String, Long> repeatedResolutions
) {
}
