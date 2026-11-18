package com.iitsm.controller.api;

import com.iitsm.controller.dto.CreateIncidentApiRequest;
import com.iitsm.controller.dto.IncidentResponseDto;
import com.iitsm.controller.dto.TransitionIncidentApiRequest;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.Role;
import com.iitsm.domain.enums.SlaStatus;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.repository.IncidentSearchCriteria;
import com.iitsm.service.IncidentService;
import com.iitsm.service.UserService;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin REST controller exposing /api/v1/incidents endpoints secured by HTTP Basic and role checks. */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentRestController {

    private final IncidentService incidentService;
    private final UserService userService;

    /** Constructs IncidentRestController delegating all domain logic to IncidentService. */
    public IncidentRestController(IncidentService incidentService, UserService userService) {
        this.incidentService = incidentService;
        this.userService = userService;
    }

    /** Lists role-scoped incidents paginated. */
    @GetMapping
    public Page<IncidentResponseDto> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          Authentication authentication) {
        return search(null, null, null, null, null, null, null, page, size, authentication);
    }

    /** Searches role-scoped incidents using combinable JPA Specification filters. */
    @GetMapping("/search")
    public Page<IncidentResponseDto> search(@RequestParam(required = false) String incidentId,
                                            @RequestParam(required = false) IncidentStatus status,
                                            @RequestParam(required = false) Priority priority,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Long serviceId,
                                            @RequestParam(required = false) SlaStatus slaStatus,
                                            @RequestParam(required = false) String freeText,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            Authentication authentication) {
        User actor = userService.getByUsername(authentication.getName());
        String reporterScope = actor.getRole() == Role.EMPLOYEE ? actor.getUserCode() : null;
        String techScope = actor.getRole() == Role.TECHNICIAN ? actor.getUserCode() : null;
        IncidentSearchCriteria criteria = new IncidentSearchCriteria(
                incidentId, null, null, status, priority, categoryId, serviceId,
                techScope, reporterScope, slaStatus, freeText);
        return incidentService.search(
                        criteria, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(IncidentResponseDto::from);
    }

    /** Fetches a single incident enforcing role ownership rules. */
    @GetMapping("/{id}")
    public IncidentResponseDto getOne(@PathVariable String id, Authentication authentication) {
        User actor = userService.getByUsername(authentication.getName());
        return IncidentResponseDto.from(incidentService.getIncidentForActor(id, actor));
    }

    /** Creates a new incident through the synchronous pipeline (Employees only). */
    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<IncidentResponseDto> create(@Valid @RequestBody CreateIncidentApiRequest request,
                                                      Authentication authentication) {
        User actor = userService.getByUsername(authentication.getName());
        Incident created = incidentService.createIncident(
                actor.getUserCode(), request.serviceId(), request.categoryId(),
                request.description(), request.impact(), request.urgency(), request.affectedUsersCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentResponseDto.from(created));
    }

    /** Executes an incident lifecycle transition or action via the State pattern. */
    @PostMapping("/{id}/transition")
    public IncidentResponseDto transition(@PathVariable String id,
                                          @Valid @RequestBody TransitionIncidentApiRequest request,
                                          Authentication authentication) {
        User actor = userService.getByUsername(authentication.getName());
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        Incident updated = switch (action) {
            case "ACKNOWLEDGE" -> incidentService.acknowledge(id, actor);
            case "START" -> incidentService.startProgress(id, actor);
            case "REQUEST_INFO" -> incidentService.requestInfo(id, actor, request.noteOrReason());
            case "PROVIDE_INFO" -> incidentService.provideInfo(id, null, actor, request.noteOrReason());
            case "RESOLVE" -> incidentService.resolve(id, actor, request.summary(), request.steps());
            case "VERIFY" -> incidentService.verify(id, actor);
            case "REOPEN" -> incidentService.reopen(id, actor, request.noteOrReason());
            case "CLOSE" -> incidentService.close(id, actor, request.noteOrReason());
            case "DECLINE" -> incidentService.declineAssignment(id, actor, request.noteOrReason());
            case "ASSIGN" -> incidentService.assignTechnician(id, request.technicianCode(), actor);
            case "OVERRIDE_PRIORITY" -> incidentService.overridePriority(id, actor, request.priority(), request.noteOrReason());
            default -> throw new InvalidInputException("Unsupported transition action: " + request.action());
        };
        return IncidentResponseDto.from(updated);
    }
}
