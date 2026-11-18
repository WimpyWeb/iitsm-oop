package com.iitsm.controller;

import com.iitsm.controller.dto.EditIncidentForm;
import com.iitsm.controller.dto.ReportIncidentForm;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.User;
import com.iitsm.exception.DuplicateIncidentException;
import com.iitsm.exception.ITSMException;
import com.iitsm.service.AdminCatalogService;
import com.iitsm.service.IncidentService;
import com.iitsm.service.UserService;
import jakarta.validation.Valid;
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

/** Controller handling Employee incident reporting, tracking, edits, info replies, and verification. */
@Controller
@RequestMapping("/employee")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
public class EmployeeController {

    private final IncidentService incidentService;
    private final AdminCatalogService catalogService;
    private final UserService userService;

    /** Constructs EmployeeController with Incident, Catalog, and User services. */
    public EmployeeController(IncidentService incidentService,
                              AdminCatalogService catalogService,
                              UserService userService) {
        this.incidentService = incidentService;
        this.catalogService = catalogService;
        this.userService = userService;
    }

    /** Displays the signed-in employee's paginated incident dashboard. */
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "0") int page,
                            Authentication authentication,
                            Model model) {
        User user = userService.getByUsername(authentication.getName());
        Page<Incident> myIncidents = incidentService.findByReporter(user.getUserCode(), PageRequest.of(page, 15));
        model.addAttribute("incidents", myIncidents);
        return "employee/dashboard";
    }

    /** Renders the new incident submission form with DB services and categories. */
    @GetMapping("/incidents/new")
    public String newIncidentForm(Model model) {
        model.addAttribute("form", ReportIncidentForm.defaults());
        model.addAttribute("services", catalogService.listServicesWithDependencies());
        model.addAttribute("categories", catalogService.listCategories());
        return "employee/report";
    }

    /** Submits a new incident through the synchronous NEW -> CLASSIFIED -> ASSIGNED pipeline. */
    @PostMapping("/incidents")
    public String createIncident(@Valid @ModelAttribute("form") ReportIncidentForm form,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("services", catalogService.listServicesWithDependencies());
            model.addAttribute("categories", catalogService.listCategories());
            return "employee/report";
        }
        User user = userService.getByUsername(authentication.getName());
        try {
            Incident created = incidentService.createIncident(
                    user.getUserCode(), form.serviceId(), form.categoryId(),
                    form.description(), form.impact(), form.urgency(), form.affectedUsersCount());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Incident " + created.getId() + " submitted (" + created.getStatus() + ").");
            return "redirect:/incidents/" + created.getId();
        } catch (DuplicateIncidentException ex) {
            model.addAttribute("services", catalogService.listServicesWithDependencies());
            model.addAttribute("categories", catalogService.listCategories());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("duplicateIncidentId", ex.getExistingIncidentId());
            return "employee/report";
        } catch (ITSMException ex) {
            model.addAttribute("services", catalogService.listServicesWithDependencies());
            model.addAttribute("categories", catalogService.listCategories());
            model.addAttribute("errorMessage", ex.getMessage());
            return "employee/report";
        }
    }

    /** Updates description, impact, urgency, or affectedUsers prior to resolution. */
    @PostMapping("/incidents/{id}/edit")
    public String editIncident(@PathVariable String id,
                               @Valid @ModelAttribute("editForm") EditIncidentForm form,
                               BindingResult bindingResult,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid update fields supplied.");
            return "redirect:/incidents/" + id;
        }
        User actor = userService.getByUsername(authentication.getName());
        incidentService.updateIncidentDetails(
                id, actor, form.description(), form.impact(), form.urgency(), form.affectedUsersCount());
        redirectAttributes.addFlashAttribute("successMessage", "Incident updated and priority recalculated.");
        return "redirect:/incidents/" + id;
    }

    /** Answers a technician's clarification request and resumes IN_PROGRESS. */
    @PostMapping("/incidents/{id}/answer-info")
    public String answerInfo(@PathVariable String id,
                             @RequestParam(required = false) Long infoRequestId,
                             @RequestParam String answer,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.provideInfo(id, infoRequestId, actor, answer);
        redirectAttributes.addFlashAttribute("successMessage", "Information submitted; investigation resumed.");
        return "redirect:/incidents/" + id;
    }

    /** Adds supplemental reporter notes to an active incident. */
    @PostMapping("/incidents/{id}/note")
    public String addNote(@PathVariable String id,
                          @RequestParam String noteText,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.addInvestigationNote(id, actor, noteText);
        redirectAttributes.addFlashAttribute("successMessage", "Note added to incident timeline.");
        return "redirect:/incidents/" + id;
    }

    /** Confirms fix (RESOLVED -> VERIFIED). */
    @PostMapping("/incidents/{id}/verify")
    public String verify(@PathVariable String id,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.verify(id, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Resolution verified.");
        return "redirect:/incidents/" + id;
    }

    /** Rejects fix and reopens incident (RESOLVED -> IN_PROGRESS). */
    @PostMapping("/incidents/{id}/reopen")
    public String reopen(@PathVariable String id,
                         @RequestParam String reason,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.reopen(id, actor, reason);
        redirectAttributes.addFlashAttribute("successMessage", "Incident reopened for further investigation.");
        return "redirect:/incidents/" + id;
    }

    /** Submits post-resolution rating and comment. */
    @PostMapping("/incidents/{id}/feedback")
    public String feedback(@PathVariable String id,
                           @RequestParam int rating,
                           @RequestParam(required = false) String comment,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        User actor = userService.getByUsername(authentication.getName());
        incidentService.submitFeedback(id, actor, rating, comment);
        redirectAttributes.addFlashAttribute("successMessage", "Thank you for your feedback.");
        return "redirect:/incidents/" + id;
    }
}
