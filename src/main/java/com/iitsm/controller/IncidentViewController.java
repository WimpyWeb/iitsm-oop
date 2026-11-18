package com.iitsm.controller;

import com.iitsm.controller.dto.EditIncidentForm;
import com.iitsm.controller.dto.ResolveIncidentForm;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.Role;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.User;
import com.iitsm.intelligence.SlaRiskPredictor;
import com.iitsm.repository.IncidentSearchCriteria;
import com.iitsm.service.AdminCatalogService;
import com.iitsm.service.AuditService;
import com.iitsm.service.IncidentService;
import com.iitsm.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Shared incident detail, audit timeline, combinable search, and live HTMX suggestion controller. */
@Controller
@RequestMapping("/incidents")
public class IncidentViewController {

    private final IncidentService incidentService;
    private final AuditService auditService;
    private final AdminCatalogService catalogService;
    private final UserService userService;
    private final SlaRiskPredictor slaRiskPredictor;

    /** Constructs IncidentViewController with required incident and catalog services. */
    public IncidentViewController(IncidentService incidentService,
                                  AuditService auditService,
                                  AdminCatalogService catalogService,
                                  UserService userService,
                                  SlaRiskPredictor slaRiskPredictor) {
        this.incidentService = incidentService;
        this.auditService = auditService;
        this.catalogService = catalogService;
        this.userService = userService;
        this.slaRiskPredictor = slaRiskPredictor;
    }

    /** Displays full incident details, SLA risk, AI suggestions, actions, and audit timeline. */
    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Authentication authentication, Model model) {
        User actor = userService.getByUsername(authentication.getName());
        Incident incident = incidentService.getIncidentForActor(id, actor);

        model.addAttribute("incident", incident);
        model.addAttribute("timeline", auditService.getTimelineForIncident(incident.getId()));
        model.addAttribute("slaRisk", slaRiskPredictor.assess(incident, Instant.now()));
        model.addAttribute("slaViolations", catalogService.listSlaViolationsForIncident(incident.getId()));
        model.addAttribute("suggestedResolution",
                incidentService.getSuggestedResolution(incident).orElse(null));
        model.addAttribute("editForm", new EditIncidentForm(
                incident.getDescription(), incident.getImpact(),
                incident.getUrgency(), incident.getAffectedUsersCount()));
        model.addAttribute("resolveForm", new ResolveIncidentForm("", ""));
        model.addAttribute("priorities", Priority.values());

        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.TECHNICIAN) {
            model.addAttribute("technicians", userService.listActiveTechnicians());
            model.addAttribute("recommendations", incidentService.getTechnicianRecommendations(incident));
        }
        return "incidents/detail";
    }

    /** Executes combinable role-scoped search across incident attributes via JPA Specifications. */
    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String incidentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String technicianCode,
            @RequestParam(required = false) SlaStatus slaStatus,
            @RequestParam(required = false) String freeText,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model) {

        User actor = userService.getByUsername(authentication.getName());
        String scopedReporter = actor.getRole() == Role.EMPLOYEE ? actor.getUserCode() : null;
        String scopedTechnician = actor.getRole() == Role.TECHNICIAN ? actor.getUserCode() : technicianCode;

        IncidentSearchCriteria criteria = new IncidentSearchCriteria(
                incidentId, fromDate, toDate, status, priority, categoryId,
                serviceId, scopedTechnician, scopedReporter, slaStatus, freeText);

        Page<Incident> results = incidentService.search(
                criteria, PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "createdAt")));

        model.addAttribute("results", results);
        model.addAttribute("criteria", criteria);
        model.addAttribute("statuses", IncidentStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("slaStatuses", SlaStatus.values());
        model.addAttribute("categories", catalogService.listCategories());
        model.addAttribute("services", catalogService.listServicesWithDependencies());
        model.addAttribute("technicians", userService.listActiveTechnicians());
        return "incidents/search";
    }

    /** HTMX fragment returning live auto-category suggestion and similar 24h incident warnings. */
    @GetMapping("/suggest")
    public String liveSuggestions(@RequestParam(required = false) Long serviceId,
                                  @RequestParam(required = false) String description,
                                  Model model) {
        String suggestedCat = incidentService.suggestCategory(description)
                .map(c -> c.getName())
                .orElse(null);
        List<Incident> related = (serviceId != null && description != null && description.length() >= 5)
                ? incidentService.findRelatedCandidates(serviceId, description)
                : List.of();
        model.addAttribute("suggestedCategory", suggestedCat);
        model.addAttribute("relatedCandidates", related);
        return "incidents/fragments :: suggestions";
    }
}
