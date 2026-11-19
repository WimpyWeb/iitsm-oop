package com.iitsm.controller;

import com.iitsm.controller.dto.CreateCategoryForm;
import com.iitsm.controller.dto.CreateServiceForm;
import com.iitsm.controller.dto.CreateUserForm;
import com.iitsm.controller.dto.SlaPolicyForm;
import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.User;
import com.iitsm.exception.ITSMException;
import com.iitsm.repository.IncidentSearchCriteria;
import com.iitsm.service.AdminCatalogService;
import com.iitsm.service.AuditService;
import com.iitsm.service.IncidentService;
import com.iitsm.service.UserService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin controller managing incidents, strategies, users, teams, catalog, audit logs, and SLA violations. */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final IncidentService incidentService;
    private final AdminCatalogService catalogService;
    private final UserService userService;
    private final AuditService auditService;

    /** Constructs AdminController with all required admin services. */
    public AdminController(IncidentService incidentService,
                           AdminCatalogService catalogService,
                           UserService userService,
                           AuditService auditService) {
        this.incidentService = incidentService;
        this.catalogService = catalogService;
        this.userService = userService;
        this.auditService = auditService;
    }

    /** Displays global monitoring dashboard, unassigned priority queue, and runtime strategy switcher. */
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("unassignedQueue", incidentService.getUnassignedQueue());
        model.addAttribute("allIncidents", incidentService.search(
                IncidentSearchCriteria.empty(),
                PageRequest.of(page, 15, Sort.by(Sort.Direction.DESC, "createdAt"))));
        model.addAttribute("technicians", userService.listActiveTechnicians());
        model.addAttribute("activePriorityStrategy", catalogService.getActivePriorityStrategy().getName());
        model.addAttribute("activeAssignmentStrategy", catalogService.getActiveAssignmentStrategy().getName());
        model.addAttribute("priorityStrategies", catalogService.getAvailablePriorityStrategies());
        model.addAttribute("assignmentStrategies", catalogService.getAvailableAssignmentStrategies());
        model.addAttribute("escalationMinPriority", catalogService.getEscalationMinPriority());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("recentViolationsCount",
                catalogService.listSlaViolations(PageRequest.of(0, 1)).getTotalElements());
        return "admin/dashboard";
    }

    /** Updates active PriorityStrategy, AssignmentStrategy, and escalation threshold at runtime. */
    @PostMapping("/strategies")
    public String updateStrategies(@RequestParam String priorityStrategy,
                                   @RequestParam String assignmentStrategy,
                                   @RequestParam Priority escalationMinPriority,
                                   RedirectAttributes redirectAttributes) {
        catalogService.setActivePriorityStrategy(priorityStrategy);
        catalogService.setActiveAssignmentStrategy(assignmentStrategy);
        catalogService.setEscalationMinPriority(escalationMinPriority);
        redirectAttributes.addFlashAttribute("successMessage",
                "Active strategies updated: " + priorityStrategy + " / " + assignmentStrategy);
        return "redirect:/admin/dashboard";
    }

    /** Manually assigns or reassigns an incident to a technician. */
    @PostMapping("/incidents/{id}/assign")
    public String assignIncident(@PathVariable String id,
                                 @RequestParam String technicianCode,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.assignTechnician(id, technicianCode, actor);
        redirectAttributes.addFlashAttribute("successMessage",
                "Incident " + id + " assigned to " + technicianCode + ".");
        return "redirect:/incidents/" + id;
    }

    /** Overrides incident priority from admin views. */
    @PostMapping("/incidents/{id}/priority")
    public String overridePriority(@PathVariable String id,
                                   @RequestParam Priority priority,
                                   @RequestParam(required = false) String reason,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.overridePriority(id, actor, priority, reason);
        redirectAttributes.addFlashAttribute("successMessage", "Priority overridden to " + priority.getDisplayName() + ".");
        return "redirect:/incidents/" + id;
    }

    /** Closes a VERIFIED incident from admin controls. */
    @PostMapping("/incidents/{id}/close")
    public String closeIncident(@PathVariable String id,
                                @RequestParam(required = false) String closureNotes,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.close(id, actor, closureNotes);
        redirectAttributes.addFlashAttribute("successMessage", "Incident " + id + " closed.");
        return "redirect:/incidents/" + id;
    }

    /** Lists all user accounts and renders provisioning form. */
    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("users", userService.listAllUsers(PageRequest.of(page, 20)));
        model.addAttribute("adminLevels", AdminLevel.values());
        return "admin/users";
    }

    /** Provisions a new Employee, Technician, TeamLead, or Administrator account. */
    @PostMapping("/users")
    public String createUser(@Valid @ModelAttribute CreateUserForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid user fields (min password 8 chars).");
            return "redirect:/admin/users";
        }
        try {
            List<String> skills = parseCsv(form.skillsCsv());
            switch (form.accountType().toUpperCase()) {
                case "TECHNICIAN" -> userService.createTechnician(
                        form.userCode(), form.username(), form.email(), form.fullName(),
                        form.password(), form.phone(), skills, form.experienceYears(), form.maxLoad());
                case "TEAM_LEAD" -> userService.createTeamLead(
                        form.userCode(), form.username(), form.email(), form.fullName(),
                        form.password(), form.phone(), skills, form.experienceYears(), form.maxLoad());
                case "ADMIN" -> userService.createAdministrator(
                        form.userCode(), form.username(), form.email(), form.fullName(),
                        form.password(), form.phone(),
                        form.adminLevel() == null ? AdminLevel.SYSTEM_ADMIN : form.adminLevel());
                default -> userService.registerEmployee(
                        form.username(), form.email(), form.fullName(),
                        form.password(), form.phone(), form.department(), form.jobTitle());
            }
            redirectAttributes.addFlashAttribute("successMessage", "Account created for " + form.username() + ".");
        } catch (ITSMException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    /** Activates or deactivates a user account. */
    @PostMapping("/users/{id}/toggle-active")
    public String toggleUserActive(@PathVariable Long id,
                                   @RequestParam boolean active,
                                   RedirectAttributes redirectAttributes) {
        userService.setUserActive(id, active);
        redirectAttributes.addFlashAttribute("successMessage", "User status updated.");
        return "redirect:/admin/users";
    }

    /** Displays support teams, team leads, and members. */
    @GetMapping("/teams")
    public String teams(Model model) {
        model.addAttribute("teams", catalogService.listTeamsWithMembers());
        model.addAttribute("teamLeads", userService.listActiveTeamLeads());
        model.addAttribute("technicians", userService.listActiveTechnicians());
        return "admin/teams";
    }

    /** Creates a new support team with optional lead and initial members. */
    @PostMapping("/teams")
    public String createTeam(@RequestParam String name,
                             @RequestParam(required = false) String specialtyCategory,
                             @RequestParam(required = false) String teamLeadCode,
                             @RequestParam(required = false) List<String> memberCodes,
                             RedirectAttributes redirectAttributes) {
        catalogService.createTeam(name, specialtyCategory, teamLeadCode, memberCodes);
        redirectAttributes.addFlashAttribute("successMessage", "Team '" + name + "' created.");
        return "redirect:/admin/teams";
    }

    /** Assigns an existing technician to a support team. */
    @PostMapping("/teams/{id}/assign")
    public String assignMember(@PathVariable Long id,
                               @RequestParam String technicianCode,
                               RedirectAttributes redirectAttributes) {
        catalogService.assignMemberToTeam(id, technicianCode);
        redirectAttributes.addFlashAttribute("successMessage", "Assigned " + technicianCode + " to team.");
        return "redirect:/admin/teams";
    }

    /** Displays catalog CRUD for categories, services, dependency graph, and SLA policies. */
    @GetMapping("/catalog")
    public String catalog(Model model) {
        var services = catalogService.listServicesWithDependencies();
        model.addAttribute("categories", catalogService.listCategories());
        model.addAttribute("services", services);
        model.addAttribute("slaPolicies", catalogService.listSlaPolicies());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("dependencyMermaid", buildDependencyMermaid(services));
        return "admin/catalog";
    }

    /** Creates a new DB-driven incident category without any code changes. */
    @PostMapping("/catalog/categories")
    public String createCategory(@Valid @ModelAttribute CreateCategoryForm form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Category name is required.");
            return "redirect:/admin/catalog";
        }
        catalogService.createCategory(form.name(), parseCsv(form.keywordsCsv()));
        redirectAttributes.addFlashAttribute("successMessage", "Category '" + form.name() + "' saved.");
        return "redirect:/admin/catalog";
    }

    /** Updates an existing category name and keywords. */
    @PostMapping("/catalog/categories/{id}")
    public String updateCategory(@PathVariable Long id,
                                 @Valid @ModelAttribute CreateCategoryForm form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Category name is required.");
            return "redirect:/admin/catalog";
        }
        catalogService.updateCategory(id, form.name(), parseCsv(form.keywordsCsv()));
        redirectAttributes.addFlashAttribute("successMessage", "Category '" + form.name() + "' updated.");
        return "redirect:/admin/catalog";
    }

    /** Deletes an unused category. */
    @PostMapping("/catalog/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        catalogService.deleteCategory(id);
        redirectAttributes.addFlashAttribute("successMessage", "Category deleted.");
        return "redirect:/admin/catalog";
    }

    /** Creates a service catalog entry with optional SLA overrides and upstream dependencies. */
    @PostMapping("/catalog/services")
    public String createService(@Valid @ModelAttribute CreateServiceForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid service details.");
            return "redirect:/admin/catalog";
        }
        catalogService.createService(
                form.name(), form.description(), form.businessCriticality(),
                parseCsv(form.keywordsCsv()), form.overrideResponseMins(),
                form.overrideResolutionMins(), form.dependsOnIds());
        redirectAttributes.addFlashAttribute("successMessage", "Service '" + form.name() + "' created.");
        return "redirect:/admin/catalog";
    }

    /** Updates an existing service catalog entry, SLA overrides, and dependencies. */
    @PostMapping("/catalog/services/{id}")
    public String updateService(@PathVariable Long id,
                                @Valid @ModelAttribute CreateServiceForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid service details.");
            return "redirect:/admin/catalog";
        }
        catalogService.updateService(
                id, form.name(), form.description(), form.businessCriticality(),
                parseCsv(form.keywordsCsv()), form.overrideResponseMins(),
                form.overrideResolutionMins(), form.dependsOnIds());
        redirectAttributes.addFlashAttribute("successMessage", "Service '" + form.name() + "' updated.");
        return "redirect:/admin/catalog";
    }

    /** Creates or updates an SLA policy for a priority tier. */
    @PostMapping("/catalog/sla")
    public String saveSlaPolicy(@Valid @ModelAttribute SlaPolicyForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid SLA duration values.");
            return "redirect:/admin/catalog";
        }
        catalogService.saveOrUpdateSlaPolicy(form.priority(), form.responseMins(), form.resolutionMins());
        redirectAttributes.addFlashAttribute("successMessage", "SLA policy saved for " + form.priority() + ".");
        return "redirect:/admin/catalog";
    }

    /** Displays global filterable immutable audit log. */
    @GetMapping("/audit")
    public String auditLog(@RequestParam(required = false) String incidentId,
                           @RequestParam(required = false) String actor,
                           @RequestParam(required = false) String action,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        model.addAttribute("entries", auditService.search(incidentId, actor, action, PageRequest.of(page, 25)));
        model.addAttribute("incidentId", incidentId);
        model.addAttribute("actor", actor);
        model.addAttribute("action", action);
        return "admin/audit";
    }

    /** Displays persisted SLA violations list. */
    @GetMapping("/sla-violations")
    public String slaViolations(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("violations", catalogService.listSlaViolations(PageRequest.of(page, 20)));
        return "admin/sla-violations";
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String buildDependencyMermaid(List<com.iitsm.domain.model.ServiceEntity> services) {
        StringBuilder sb = new StringBuilder("graph LR\n");
        for (var svc : services) {
            String safeId = "S" + svc.getId();
            sb.append("  ").append(safeId).append("[\"").append(svc.getName()).append("\"]\n");
            for (var dep : svc.getDependsOn()) {
                sb.append("  ").append(safeId).append(" --> S").append(dep.getId()).append("\n");
            }
        }
        return sb.toString();
    }
}
