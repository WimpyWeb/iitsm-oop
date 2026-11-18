package com.iitsm.controller;

import com.iitsm.controller.dto.ResolveIncidentForm;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.service.IncidentService;
import com.iitsm.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

/** Controller handling Technician workload, availability, recommendations, and incident resolution. */
@Controller
@RequestMapping("/technician")
@PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
public class TechnicianController {

    private final IncidentService incidentService;
    private final UserService userService;

    /** Constructs TechnicianController with IncidentService and UserService. */
    public TechnicianController(IncidentService incidentService, UserService userService) {
        this.incidentService = incidentService;
        this.userService = userService;
    }

    /** Displays assigned incidents, workload meter, availability toggle, and recommended unassigned items. */
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "0") int page,
                            Authentication authentication,
                            Model model) {
        User user = userService.getByUsername(authentication.getName());
        Page<Incident> assigned = incidentService.findByTechnician(user.getUserCode(), PageRequest.of(page, 15));
        List<Incident> recommended = (user instanceof Technician tech)
                ? incidentService.getRecommendedUnassignedForTechnician(tech)
                : incidentService.getUnassignedQueue();

        model.addAttribute("assignedIncidents", assigned);
        model.addAttribute("recommendedIncidents", recommended);
        if (user instanceof Technician tech) {
            model.addAttribute("technicianProfile", tech);
        }
        return "technician/dashboard";
    }

    /** Toggles the signed-in technician's availability flag. */
    @PostMapping("/availability")
    public String toggleAvailability(Authentication authentication, RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        Technician updated = userService.toggleAvailability(actor.getUserCode(), actor);
        redirectAttributes.addFlashAttribute("successMessage",
                "Availability set to " + (updated.isAvailable() ? "AVAILABLE" : "UNAVAILABLE") + ".");
        return "redirect:/technician/dashboard";
    }

    /** Self-claims an unassigned incident from the recommendations list. */
    @PostMapping("/incidents/{id}/claim")
    public String claim(@PathVariable String id,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.claimUnassignedIncident(id, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Claimed incident " + id + ".");
        return "redirect:/incidents/" + id;
    }

    /** Acknowledges an assigned incident (ASSIGNED -> ACKNOWLEDGED). */
    @PostMapping("/incidents/{id}/acknowledge")
    public String acknowledge(@PathVariable String id,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.acknowledge(id, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Incident acknowledged.");
        return "redirect:/incidents/" + id;
    }

    /** Starts investigation (ACKNOWLEDGED -> IN_PROGRESS). */
    @PostMapping("/incidents/{id}/start")
    public String startProgress(@PathVariable String id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.startProgress(id, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Investigation started.");
        return "redirect:/incidents/" + id;
    }

    /** Appends a technician investigation note. */
    @PostMapping("/incidents/{id}/note")
    public String addNote(@PathVariable String id,
                          @RequestParam String noteText,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.addInvestigationNote(id, actor, noteText);
        redirectAttributes.addFlashAttribute("successMessage", "Investigation note recorded.");
        return "redirect:/incidents/" + id;
    }

    /** Requests additional info from the reporter (IN_PROGRESS -> PENDING_INFO). */
    @PostMapping("/incidents/{id}/request-info")
    public String requestInfo(@PathVariable String id,
                              @RequestParam String question,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.requestInfo(id, actor, question);
        redirectAttributes.addFlashAttribute("successMessage", "Information requested from reporter.");
        return "redirect:/incidents/" + id;
    }

    /** Marks incident resolved with summary and steps (IN_PROGRESS -> RESOLVED). */
    @PostMapping("/incidents/{id}/resolve")
    public String resolve(@PathVariable String id,
                          @Valid @ModelAttribute("resolveForm") ResolveIncidentForm form,
                          BindingResult bindingResult,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Summary and steps are required to resolve.");
            return "redirect:/incidents/" + id;
        }
        User actor = userService.getByUsername(authentication.getName());
        incidentService.resolve(id, actor, form.summary(), form.steps());
        redirectAttributes.addFlashAttribute("successMessage", "Incident marked RESOLVED.");
        return "redirect:/incidents/" + id;
    }

    /** Closes a VERIFIED incident (VERIFIED -> CLOSED). */
    @PostMapping("/incidents/{id}/close")
    public String close(@PathVariable String id,
                        @RequestParam(required = false) String closureNotes,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.close(id, actor, closureNotes);
        redirectAttributes.addFlashAttribute("successMessage", "Incident CLOSED.");
        return "redirect:/incidents/" + id;
    }

    /** Declines an assignment with a reason and triggers automatic reassignment. */
    @PostMapping("/incidents/{id}/decline")
    public String decline(@PathVariable String id,
                          @RequestParam String reason,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.declineAssignment(id, actor, reason);
        redirectAttributes.addFlashAttribute("successMessage",
                "Assignment declined; incident returned for reassignment.");
        return "redirect:/technician/dashboard";
    }

    /** Overrides incident priority from the technician detail view. */
    @PostMapping("/incidents/{id}/priority")
    public String overridePriority(@PathVariable String id,
                                   @RequestParam Priority priority,
                                   @RequestParam(required = false) String reason,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.overridePriority(id, actor, priority, reason);
        redirectAttributes.addFlashAttribute("successMessage", "Priority updated to " + priority.getDisplayName() + ".");
        return "redirect:/incidents/" + id;
    }
}
