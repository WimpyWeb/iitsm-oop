package com.iitsm.service;

import com.iitsm.controller.dto.DashboardReportDto;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import com.iitsm.intelligence.TfIdfSimilarityEngine;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.SlaViolationRepository;
import com.iitsm.repository.TechnicianRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Analytics service computing overview KPIs, SLA compliance, workload, feedback, and recurring problems. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final int RECURRING_THRESHOLD = 3;

    private final IncidentRepository incidentRepository;
    private final TechnicianRepository technicianRepository;
    private final SlaViolationRepository slaViolationRepository;
    private final TfIdfSimilarityEngine similarityEngine;

    /** Constructs ReportService with repositories and keyword analyzer. */
    public ReportService(IncidentRepository incidentRepository,
                         TechnicianRepository technicianRepository,
                         SlaViolationRepository slaViolationRepository,
                         TfIdfSimilarityEngine similarityEngine) {
        this.incidentRepository = incidentRepository;
        this.technicianRepository = technicianRepository;
        this.slaViolationRepository = slaViolationRepository;
        this.similarityEngine = similarityEngine;
    }

    /** Generates a complete live operational report snapshot across all incidents and technicians. */
    public DashboardReportDto generateReport() {
        List<Incident> all = incidentRepository.findAll();
        long total = all.size();
        long open = all.stream().filter(i -> i.getStatus().isOpen()).count();
        long critical = all.stream().filter(i -> i.getPriority() == Priority.P1).count();
        long resolved = all.stream().filter(i -> !i.getStatus().isOpen()).count();
        long violations = slaViolationRepository.count();

        long withSla = all.stream().filter(i -> i.getSla() != null && i.getSla().getSlaStatus() != null).count();
        long breached = all.stream()
                .filter(i -> i.getSla() != null && i.getSla().getSlaStatus() == SlaStatus.BREACHED)
                .count();
        double slaCompliance = withSla == 0 ? 100.0 : round1(((withSla - breached) * 100.0) / withSla);

        double avgResMins = round1(all.stream()
                .filter(i -> i.getResolvedAt() != null)
                .mapToLong(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                .average()
                .orElse(0.0));

        double avgRating = round2(all.stream()
                .filter(i -> i.getFeedback() != null && i.getFeedback().getRating() != null)
                .mapToInt(i -> i.getFeedback().getRating())
                .average()
                .orElse(0.0));

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            byPriority.put(p.getDisplayName(), all.stream().filter(i -> i.getPriority() == p).count());
        }

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (IncidentStatus s : IncidentStatus.values()) {
            long c = all.stream().filter(i -> i.getStatus() == s).count();
            if (c > 0) {
                byStatus.put(s.name(), c);
            }
        }

        Map<String, Long> byService = all.stream()
                .filter(i -> i.getAffectedService() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getAffectedService().getName(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        Map<String, Long> techWorkload = new LinkedHashMap<>();
        for (Technician t : technicianRepository.findAllActiveWithTeam()) {
            techWorkload.put(t.getUserCode() + " (" + t.getFullName() + ")", (long) t.getCurrentLoad());
        }

        // Recurring problems over last 7 days
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Incident> last7Days = all.stream()
                .filter(i -> i.getCreatedAt().isAfter(sevenDaysAgo))
                .toList();

        Map<String, Long> recurringServices = filterRecurringCounts(last7Days.stream()
                .filter(i -> i.getAffectedService() != null)
                .collect(Collectors.groupingBy(i -> i.getAffectedService().getName(), Collectors.counting())));

        Map<String, Long> recurringCategories = filterRecurringCounts(last7Days.stream()
                .filter(i -> i.getCategory() != null)
                .collect(Collectors.groupingBy(i -> i.getCategory().getName(), Collectors.counting())));

        List<String> topKeywords = similarityEngine.topKeywords(
                last7Days.stream().map(Incident::getDescription).toList(), 8);

        Map<String, Long> repeatedResolutions = all.stream()
                .filter(i -> i.getResolution() != null && i.getResolution().getSummary() != null)
                .collect(Collectors.groupingBy(i -> i.getResolution().getSummary(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        return new DashboardReportDto(
                total, open, critical, resolved, violations, slaCompliance,
                avgResMins, avgRating, byPriority, byStatus, byService, techWorkload,
                recurringServices, recurringCategories, new ArrayList<>(topKeywords), repeatedResolutions);
    }

    private Map<String, Long> filterRecurringCounts(Map<String, Long> rawCounts) {
        return rawCounts.entrySet().stream()
                .filter(e -> e.getValue() >= RECURRING_THRESHOLD)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
