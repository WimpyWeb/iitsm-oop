package com.iitsm.service;

import com.iitsm.domain.enums.IncidentEventType;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.enums.Role;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.SlaInfo;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.observer.IncidentEvent;
import com.iitsm.domain.observer.IncidentEventPublisher;
import com.iitsm.domain.strategy.PriorityAssessment;
import com.iitsm.exception.DuplicateIncidentException;
import com.iitsm.exception.IncidentNotFoundException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.MissingSlaException;
import com.iitsm.exception.PersistenceFailureException;
import com.iitsm.exception.ServiceNotFoundException;
import com.iitsm.exception.UnauthorizedException;
import com.iitsm.intelligence.AiTextService;
import com.iitsm.intelligence.OutageCorrelator;
import com.iitsm.intelligence.TechnicianRecommender;
import com.iitsm.intelligence.TechnicianRecommender.TechnicianRecommendation;
import com.iitsm.intelligence.TfIdfSimilarityEngine;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.EmployeeRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.IncidentSearchCriteria;
import com.iitsm.repository.IncidentSpecifications;
import com.iitsm.repository.ServiceRepository;
import com.iitsm.repository.SlaPolicyRepository;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional application service orchestrating incident creation, lifecycle transitions, and search. */
@Service
@Transactional
public class IncidentService {

    private static final double EXACT_DUPLICATE_THRESHOLD = 0.8;
    private static final double RELATED_SIMILARITY_THRESHOLD = 0.2;

    private final IncidentRepository incidentRepository;
    private final EmployeeRepository employeeRepository;
    private final TechnicianRepository technicianRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final IncidentFactory incidentFactory;
    private final IncidentEventPublisher eventPublisher;
    private final AdminCatalogService adminCatalogService;
    private final TfIdfSimilarityEngine similarityEngine;
    private final AiTextService aiTextService;
    private final OutageCorrelator outageCorrelator;
    private final TechnicianRecommender technicianRecommender;
    private final AuditService auditService;
    private volatile int slaTimeScale;

    /** Constructs IncidentService with all required domain, repository, and intelligence collaborators. */
    public IncidentService(IncidentRepository incidentRepository,
                           EmployeeRepository employeeRepository,
                           TechnicianRepository technicianRepository,
                           UserRepository userRepository,
                           ServiceRepository serviceRepository,
                           CategoryRepository categoryRepository,
                           SlaPolicyRepository slaPolicyRepository,
                           IncidentFactory incidentFactory,
                           IncidentEventPublisher eventPublisher,
                           AdminCatalogService adminCatalogService,
                           TfIdfSimilarityEngine similarityEngine,
                           AiTextService aiTextService,
                           OutageCorrelator outageCorrelator,
                           TechnicianRecommender technicianRecommender,
                           AuditService auditService,
                           @Value("${app.sla.time-scale:1}") int slaTimeScale) {
        this.incidentRepository = incidentRepository;
        this.employeeRepository = employeeRepository;
        this.technicianRepository = technicianRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.categoryRepository = categoryRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.incidentFactory = incidentFactory;
        this.eventPublisher = eventPublisher;
        this.adminCatalogService = adminCatalogService;
        this.similarityEngine = similarityEngine;
        this.aiTextService = aiTextService;
        this.outageCorrelator = outageCorrelator;
        this.technicianRecommender = technicianRecommender;
        this.auditService = auditService;
        this.slaTimeScale = Math.max(1, slaTimeScale);
    }

    /** Synchronizes INC1001+ generator above any persisted IDs on application startup. */
    @PostConstruct
    public void syncSequence() {
        long max = 1000;
        for (String id : incidentRepository.findAllIds()) {
            if (id != null && id.startsWith("INC")) {
                try {
                    long num = Long.parseLong(id.substring(3));
                    if (num > max) {
                        max = num;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore non-standard seed IDs.
                }
            }
        }
        incidentFactory.syncSequenceAbove(max);
    }

    public void setSlaTimeScale(int slaTimeScale) {
        this.slaTimeScale = Math.max(1, slaTimeScale);
    }

    public int getSlaTimeScale() {
        return slaTimeScale;
    }

    /** Runs synchronous pipeline NEW -> CLASSIFIED -> ASSIGNED (or unassigned PriorityQueue). */
    public Incident createIncident(String reporterUserCode, Long serviceId, Long optionalCategoryId,
                                   String description, int impact, int urgency, int affectedUsersCount) {
        Employee reporter = employeeRepository.findByUserCodeIgnoreCase(reporterUserCode)
                .orElseThrow(() -> new UnauthorizedException("Only active employees can report incidents."));
        ServiceEntity service = serviceRepository.findByIdWithDependencies(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + serviceId));

        Instant now = Instant.now();
        Instant twentyFourHoursAgo = now.minus(24, ChronoUnit.HOURS);
        Instant tenMinutesAgo = now.minus(10, ChronoUnit.MINUTES);
        List<Incident> recentSameService = incidentRepository
                .findByAffectedServiceIdAndCreatedAtAfterOrderByCreatedAtDesc(service.getId(), twentyFourHoursAgo);

        List<String> relatedIds = detectDuplicatesAndRelated(
                description, reporter.getUserCode(), recentSameService, tenMinutesAgo);

        Incident incident = incidentFactory.createIncident(
                reporter, service, description, impact, urgency, affectedUsersCount);
        for (String relId : relatedIds) {
            incident.addRelatedIncidentId(relId);
        }

        List<Category> allCategories = categoryRepository.findAllByOrderByNameAsc();
        Optional<Category> aiSuggested = aiTextService.suggestCategory(description, allCategories);
        aiSuggested.ifPresent(cat -> incident.setSuggestedCategoryName(cat.getName()));

        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.CREATED, incident, null, IncidentStatus.NEW,
                reporter.getDisplayIdentity(),
                incident.getId() + " created by " + reporter.getDisplayIdentity()
                        + (relatedIds.isEmpty() ? "" : " (related: " + String.join(", ", relatedIds) + ")")));

        // Step 2: Classify synchronously (NEW -> CLASSIFIED).
        Category chosenCategory = resolveCategory(optionalCategoryId, aiSuggested, allCategories);
        PriorityAssessment assessment = adminCatalogService.getActivePriorityStrategy().calculatePriority(incident);
        incident.classify(chosenCategory, assessment.priority(), assessment.score());
        incident.setSla(computeSlaInfo(service, assessment.priority(), incident.getCreatedAt()));

        Set<String> outageServices = findRecentOutageServiceNames(now.minus(1, ChronoUnit.HOURS));
        outageCorrelator.correlate(incident, recentSameService, outageServices);
        saveIncident(incident);
        if (!recentSameService.isEmpty()) {
            incidentRepository.saveAll(recentSameService);
        }

        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.CLASSIFIED, incident, IncidentStatus.NEW, IncidentStatus.CLASSIFIED,
                "SYSTEM",
                incident.getId() + " classified as " + chosenCategory.getName()
                        + " with priority " + assessment.priority().getDisplayName()
                        + " (score " + assessment.score() + ")"));

        // Step 3: Auto-assign synchronously (CLASSIFIED -> ASSIGNED) or enqueue unassigned.
        attemptAutoAssign(incident, "SYSTEM");
        return hydrateAssociations(saveIncident(incident));
    }

    /** Updates reporter-editable fields while status < RESOLVED, recalculating priority and SLA. */
    public Incident updateIncidentDetails(String incidentId, User actor, String description,
                                          int impact, int urgency, int affectedUsersCount) {
        Incident incident = loadIncidentOrThrow(incidentId);
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isReporter = incident.getReporter().getUserCode().equalsIgnoreCase(actor.getUserCode());
        if (!isAdmin && !isReporter) {
            throw new UnauthorizedException("Only the reporter or an administrator may edit this incident.");
        }

        Priority oldPriority = incident.getPriority();
        incident.updateDetails(description, impact, urgency, affectedUsersCount);

        PriorityAssessment updated = adminCatalogService.getActivePriorityStrategy().calculatePriority(incident);
        boolean priorityChanged = oldPriority != updated.priority();
        incident.updatePriority(updated.priority(), updated.score(), false);
        if (priorityChanged) {
            incident.setSla(computeSlaInfo(incident.getAffectedService(), updated.priority(), incident.getCreatedAt()));
        }
        saveIncident(incident);

        if (priorityChanged) {
            eventPublisher.publish(IncidentEvent.of(
                    IncidentEventType.PRIORITY_CHANGED, incident, incident.getStatus(), incident.getStatus(),
                    actor.getDisplayIdentity(),
                    incident.getId() + " updated; priority recalculated from " + oldPriority + " to " + updated.priority()));
        } else {
            auditService.recordAction(actor.getDisplayIdentity(), "UPDATED", incident.getId(),
                    incident.getStatus(), incident.getStatus(), "Updated incident details");
        }
        return hydrateAssociations(incident);
    }

    /** Overrides incident priority manually by an admin or technician, recalculating SLA deadlines. */
    public Incident overridePriority(String incidentId, User actor, Priority newPriority, String reason) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.TECHNICIAN) {
            throw new UnauthorizedException("Only technicians or administrators may override priority.");
        }
        Incident incident = loadIncidentOrThrow(incidentId);
        Priority oldPriority = incident.getPriority();
        incident.updatePriority(newPriority, incident.getPriorityScore(), true);
        incident.setSla(computeSlaInfo(incident.getAffectedService(), newPriority, incident.getCreatedAt()));
        saveIncident(incident);

        String note = (reason == null || reason.isBlank()) ? "Manual priority override" : reason.trim();
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.PRIORITY_CHANGED, incident, incident.getStatus(), incident.getStatus(),
                actor.getDisplayIdentity(),
                "Priority overridden from " + oldPriority + " to " + newPriority + " (" + note + ")"));
        return hydrateAssociations(incident);
    }

    /** Manually assigns or reassigns a technician (validating availability and maxLoad). */
    public Incident assignTechnician(String incidentId, String technicianCode, User actor) {
        if (actor.getRole() != Role.ADMIN && !(actor instanceof TeamLead)) {
            throw new UnauthorizedException("Only administrators or team leads can manually assign incidents.");
        }
        Incident incident = loadIncidentOrThrow(incidentId);
        Technician previousTech = incident.getAssignedTechnician();
        Technician targetTech = technicianRepository.findByUserCodeIgnoreCase(technicianCode)
                .orElseThrow(() -> new InvalidInputException("Technician not found: " + technicianCode));

        IncidentStatus prevStatus = incident.getStatus();
        incident.assign(targetTech);
        if (previousTech != null && !previousTech.equals(targetTech)) {
            technicianRepository.save(previousTech);
        }
        technicianRepository.save(targetTech);
        saveIncident(incident);

        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.ASSIGNED, incident, prevStatus, incident.getStatus(),
                actor.getDisplayIdentity(),
                incident.getId() + " assigned to " + targetTech.getDisplayIdentity()));
        return hydrateAssociations(incident);
    }

    /** Allows assigned technician to decline an assignment with reason and triggers automatic reassignment. */
    public Incident declineAssignment(String incidentId, User actor, String reason) {
        Incident incident = loadIncidentOrThrow(incidentId);
        Technician tech = technicianRepository.findByUserCodeIgnoreCase(actor.getUserCode())
                .orElseThrow(() -> new UnauthorizedException("Only technicians can decline assignments."));
        IncidentStatus prevStatus = incident.getStatus();
        incident.declineAssignment(tech, reason);
        technicianRepository.save(tech);
        saveIncident(incident);

        auditService.recordAction(actor.getDisplayIdentity(), "DECLINED", incident.getId(),
                prevStatus, incident.getStatus(), "Declined assignment: " + reason);

        attemptAutoAssign(incident, "SYSTEM");
        return hydrateAssociations(saveIncident(incident));
    }

    /** Technician acknowledges an assigned incident. */
    public Incident acknowledge(String incidentId, User actor) {
        Incident incident = loadIncidentOrThrow(incidentId);
        Technician tech = requireAssignedTechnicianOrAdmin(incident, actor);
        IncidentStatus prev = incident.getStatus();
        incident.acknowledge(tech);
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.ACKNOWLEDGED, incident, prev, incident.getStatus(),
                actor.getDisplayIdentity(), incident.getId() + " acknowledged by " + actor.getDisplayIdentity()));
        return hydrateAssociations(incident);
    }

    /** Technician begins active investigation (ACKNOWLEDGED -> IN_PROGRESS). */
    public Incident startProgress(String incidentId, User actor) {
        Incident incident = loadIncidentOrThrow(incidentId);
        Technician tech = requireAssignedTechnicianOrAdmin(incident, actor);
        IncidentStatus prev = incident.getStatus();
        incident.startProgress(tech);
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.STATUS_CHANGED, incident, prev, incident.getStatus(),
                actor.getDisplayIdentity(), incident.getId() + " moved to IN_PROGRESS"));
        return hydrateAssociations(incident);
    }

    /** Adds an investigation note without changing lifecycle status. */
    public Incident addInvestigationNote(String incidentId, User actor, String noteText) {
        Incident incident = loadIncidentOrThrow(incidentId);
        ensureCanAccessIncident(incident, actor);
        incident.addInvestigationNote(actor, noteText);
        saveIncident(incident);
        auditService.recordAction(actor.getDisplayIdentity(), "NOTE_ADDED", incident.getId(),
                incident.getStatus(), incident.getStatus(), "Added note: " + noteText);
        return hydrateAssociations(incident);
    }

    /** Technician requests clarification from the reporter (IN_PROGRESS -> PENDING_INFO). */
    public Incident requestInfo(String incidentId, User actor, String question) {
        Incident incident = loadIncidentOrThrow(incidentId);
        requireAssignedTechnicianOrAdmin(incident, actor);
        IncidentStatus prev = incident.getStatus();
        incident.requestInfo(question, actor);
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.INFO_REQUESTED, incident, prev, incident.getStatus(),
                actor.getDisplayIdentity(), "Information requested: " + question));
        return hydrateAssociations(incident);
    }

    /** Reporter provides clarification and resumes investigation (PENDING_INFO -> IN_PROGRESS). */
    public Incident provideInfo(String incidentId, Long infoRequestId, User actor, String answer) {
        Incident incident = loadIncidentOrThrow(incidentId);
        requireReporterOrAdmin(incident, actor);
        IncidentStatus prev = incident.getStatus();
        incident.provideInfo(infoRequestId, answer, actor);
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.STATUS_CHANGED, incident, prev, incident.getStatus(),
                actor.getDisplayIdentity(), "Reporter answered info request; returned to IN_PROGRESS"));
        return hydrateAssociations(incident);
    }

    /** Resolves an incident (IN_PROGRESS -> RESOLVED), updating technician load and experience. */
    public Incident resolve(String incidentId, User actor, String summary, String steps) {
        Incident incident = loadIncidentOrThrow(incidentId);
        requireAssignedTechnicianOrAdmin(incident, actor);
        IncidentStatus prev = incident.getStatus();
        incident.resolve(summary, steps, actor);
        if (incident.getAssignedTechnician() != null) {
            technicianRepository.save(incident.getAssignedTechnician());
        }
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.RESOLVED, incident, prev, incident.getStatus(),
                actor.getDisplayIdentity(), "Resolved: " + summary));
        return hydrateAssociations(incident);
    }

    /** Employee verifies the resolution (RESOLVED -> VERIFIED). */
    public Incident verify(String incidentId, User actor) {
        Incident incident = loadIncidentOrThrow(incidentId);
        requireReporterOrAdmin(incident, actor);
        IncidentStatus prev = incident.getStatus();
        incident.verify(actor);
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.STATUS_CHANGED, incident, prev, incident.getStatus(),
                actor.getDisplayIdentity(), incident.getId() + " verified by " + actor.getDisplayIdentity()));
        return hydrateAssociations(incident);
    }

    /** Employee rejects the resolution and reopens the incident (RESOLVED -> IN_PROGRESS). */
    public Incident reopen(String incidentId, User actor, String reason) {
        Incident incident = loadIncidentOrThrow(incidentId);
        requireReporterOrAdmin(incident, actor);
        IncidentStatus prev = incident.getStatus();
        incident.reopen(reason, actor);
        if (incident.getAssignedTechnician() != null) {
            technicianRepository.save(incident.getAssignedTechnician());
        }
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.STATUS_CHANGED, incident, prev, incident.getStatus(),
                actor.getDisplayIdentity(), "Reopened: " + reason));
        return hydrateAssociations(incident);
    }

    /** Technician or admin closes a VERIFIED incident (VERIFIED -> CLOSED). */
    public Incident close(String incidentId, User actor, String closureNotes) {
        Incident incident = loadIncidentOrThrow(incidentId);
        if (actor.getRole() != Role.ADMIN) {
            requireAssignedTechnicianOrAdmin(incident, actor);
        }
        IncidentStatus prev = incident.getStatus();
        incident.close(closureNotes, actor);
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.CLOSED, incident, prev, incident.getStatus(),
                actor.getDisplayIdentity(), incident.getId() + " closed by " + actor.getDisplayIdentity()));
        return hydrateAssociations(incident);
    }

    /** Employee submits post-resolution rating and comment. */
    public Incident submitFeedback(String incidentId, User actor, int rating, String comment) {
        Incident incident = loadIncidentOrThrow(incidentId);
        requireReporterOrAdmin(incident, actor);
        incident.submitFeedback(rating, comment);
        saveIncident(incident);
        auditService.recordAction(actor.getDisplayIdentity(), "FEEDBACK_SUBMITTED", incident.getId(),
                incident.getStatus(), incident.getStatus(), "Rating " + rating + "/5 submitted");
        return hydrateAssociations(incident);
    }

    /** Loads incident enforcing ownership checks and initializing lazy detail collections. */
    @Transactional(readOnly = true)
    public Incident getIncidentForActor(String incidentId, User actor) {
        Incident incident = loadIncidentOrThrow(incidentId);
        ensureCanAccessIncident(incident, actor);
        return hydrateAssociations(incident);
    }

    /** Returns incidents reported by the given employee paginated. */
    @Transactional(readOnly = true)
    public Page<Incident> findByReporter(String userCode, Pageable pageable) {
        return incidentRepository.findByReporterUserCodeIgnoreCaseOrderByCreatedAtDesc(userCode, pageable);
    }

    /** Returns incidents assigned to the given technician paginated. */
    @Transactional(readOnly = true)
    public Page<Incident> findByTechnician(String userCode, Pageable pageable) {
        return incidentRepository.findByAssignedTechnicianUserCodeIgnoreCaseOrderByCreatedAtDesc(userCode, pageable);
    }

    /** Executes dynamic combinable JPA Specification search with pagination. */
    @Transactional(readOnly = true)
    public Page<Incident> search(IncidentSearchCriteria criteria, Pageable pageable) {
        return incidentRepository.findAll(IncidentSpecifications.withCriteria(criteria), pageable);
    }

    /** Returns unassigned CLASSIFIED incidents ordered by priority rank and creation time via PriorityQueue. */
    @Transactional(readOnly = true)
    public List<Incident> getUnassignedQueue() {
        List<Incident> queued = incidentRepository
                .findByStatusAndAssignedTechnicianIsNullOrderByCreatedAtAsc(IncidentStatus.CLASSIFIED);
        PriorityQueue<Incident> pq = new PriorityQueue<>(
                Comparator.comparingInt((Incident i) -> i.getPriority() == null ? 99 : i.getPriority().getRank())
                        .thenComparing(Incident::getCreatedAt));
        pq.addAll(queued);
        List<Incident> ordered = new ArrayList<>(pq.size());
        while (!pq.isEmpty()) {
            ordered.add(pq.poll());
        }
        return ordered;
    }

    /** Returns similar recent incidents on the same service (>= 0.2 cosine) for live creation warnings. */
    @Transactional(readOnly = true)
    public List<Incident> findRelatedCandidates(Long serviceId, String description) {
        if (serviceId == null || description == null || description.isBlank()) {
            return List.of();
        }
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Incident> recent = incidentRepository
                .findByAffectedServiceIdAndCreatedAtAfterOrderByCreatedAtDesc(serviceId, since);
        List<String> corpus = new ArrayList<>(recent.stream().map(Incident::getDescription).toList());
        corpus.add(description);
        return recent.stream()
                .filter(i -> similarityEngine.similarity(description, i.getDescription(), corpus)
                        >= RELATED_SIMILARITY_THRESHOLD)
                .toList();
    }

    /** Returns AI-recommended resolution guidance from similar resolved past incidents. */
    @Transactional(readOnly = true)
    public Optional<String> getSuggestedResolution(Incident incident) {
        List<Incident> resolved = incidentRepository.findAllResolvedWithResolution();
        return aiTextService.suggestResolution(incident, resolved);
    }

    /** Returns ranked technician recommendations with transparent scores for an incident. */
    @Transactional(readOnly = true)
    public List<TechnicianRecommendation> getTechnicianRecommendations(Incident incident) {
        return technicianRecommender.recommend(incident, technicianRepository.findAllActiveWithTeam());
    }

    /** Allows an eligible technician to claim an incident from the unassigned queue. */
    public Incident claimUnassignedIncident(String incidentId, User actor) {
        if (!(actor instanceof Technician tech)) {
            throw new UnauthorizedException("Only technicians may claim unassigned incidents.");
        }
        Incident incident = loadIncidentOrThrow(incidentId);
        if (incident.getAssignedTechnician() != null) {
            throw new InvalidInputException("Incident " + incidentId + " is already assigned.");
        }
        IncidentStatus prev = incident.getStatus();
        incident.assign(tech);
        technicianRepository.save(tech);
        saveIncident(incident);
        eventPublisher.publish(IncidentEvent.of(
                IncidentEventType.ASSIGNED, incident, prev, incident.getStatus(),
                tech.getDisplayIdentity(), incident.getId() + " claimed by " + tech.getDisplayIdentity()));
        return hydrateAssociations(incident);
    }

    /** Returns unassigned incidents ordered by how strongly they match this technician's skills. */
    @Transactional(readOnly = true)
    public List<Incident> getRecommendedUnassignedForTechnician(Technician tech) {
        if (tech == null) {
            return List.of();
        }
        return getUnassignedQueue().stream()
                .filter(i -> !i.getDeclinedTechnicianCodes().contains(tech.getUserCode()))
                .sorted(Comparator.comparingDouble((Incident i) ->
                        technicianRecommender.recommend(i, List.of(tech)).stream()
                                .findFirst().map(TechnicianRecommendation::score).orElse(0.0)).reversed())
                .toList();
    }

    /** Suggests a category matching the supplied incident description. */
    @Transactional(readOnly = true)
    public Optional<Category> suggestCategory(String description) {
        return aiTextService.suggestCategory(description, categoryRepository.findAllByOrderByNameAsc());
    }

    private void attemptAutoAssign(Incident incident, String actorCode) {
        List<Technician> candidates = technicianRepository.findAllActiveWithTeam();
        Optional<Technician> selected = adminCatalogService.getActiveAssignmentStrategy()
                .selectTechnician(incident, candidates);
        if (selected.isPresent()) {
            Technician tech = selected.get();
            IncidentStatus prev = incident.getStatus();
            incident.assign(tech);
            technicianRepository.save(tech);
            saveIncident(incident);
            eventPublisher.publish(IncidentEvent.of(
                    IncidentEventType.ASSIGNED, incident, prev, IncidentStatus.ASSIGNED, actorCode,
                    incident.getId() + " auto-assigned to " + tech.getDisplayIdentity()));
        } else {
            List<String> adminCodes = userRepository.findByRoleAndActiveTrue(Role.ADMIN)
                    .stream().map(User::getUserCode).toList();
            eventPublisher.publish(IncidentEvent.withRecipients(
                    IncidentEventType.CLASSIFIED, incident, IncidentStatus.CLASSIFIED, IncidentStatus.CLASSIFIED,
                    actorCode,
                    incident.getId() + " remains in unassigned queue (no eligible technician under maxLoad)",
                    adminCodes));
        }
    }

    private List<String> detectDuplicatesAndRelated(String description, String reporterCode,
                                                    List<Incident> recentSameService, Instant tenMinsAgo) {
        if (recentSameService.isEmpty()) {
            return List.of();
        }
        List<String> corpus = new ArrayList<>(recentSameService.stream().map(Incident::getDescription).toList());
        corpus.add(description);

        Set<String> related = new LinkedHashSet<>();
        for (Incident other : recentSameService) {
            double sim = similarityEngine.similarity(description, other.getDescription(), corpus);
            boolean sameReporterRecent = other.getReporter().getUserCode().equalsIgnoreCase(reporterCode)
                    && other.getCreatedAt().isAfter(tenMinsAgo);
            if (sameReporterRecent && sim >= EXACT_DUPLICATE_THRESHOLD) {
                throw new DuplicateIncidentException(other.getId());
            }
            if (sim >= RELATED_SIMILARITY_THRESHOLD) {
                related.add(other.getId());
            }
        }
        return new ArrayList<>(related);
    }

    private Category resolveCategory(Long optionalCategoryId, Optional<Category> aiSuggested, List<Category> all) {
        if (optionalCategoryId != null) {
            return categoryRepository.findById(optionalCategoryId)
                    .orElseThrow(() -> new InvalidInputException("Category not found: " + optionalCategoryId));
        }
        if (aiSuggested.isPresent()) {
            return aiSuggested.get();
        }
        return all.stream()
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("No categories exist in catalog."));
    }

    private SlaInfo computeSlaInfo(ServiceEntity service, Priority priority, Instant createdAt) {
        Optional<SlaPolicy> policyOpt = slaPolicyRepository.findByPriority(priority);
        Integer respMins = service.getOverrideResponseMins() != null
                ? service.getOverrideResponseMins()
                : policyOpt.map(SlaPolicy::getResponseMins).orElse(null);
        Integer resMins = service.getOverrideResolutionMins() != null
                ? service.getOverrideResolutionMins()
                : policyOpt.map(SlaPolicy::getResolutionMins).orElse(null);

        if (respMins == null || resMins == null) {
            throw new MissingSlaException(
                    "No SLA policy or service override configured for " + priority + " / " + service.getName());
        }
        long scale = Math.max(1, slaTimeScale);
        long respSeconds = Math.max(1L, Math.round((respMins * 60.0) / scale));
        long resSeconds = Math.max(1L, Math.round((resMins * 60.0) / scale));
        return new SlaInfo(createdAt.plusSeconds(respSeconds), createdAt.plusSeconds(resSeconds));
    }

    private Set<String> findRecentOutageServiceNames(Instant oneHourAgo) {
        List<Incident> recentAll = incidentRepository.findByCreatedAtAfterOrderByCreatedAtDesc(oneHourAgo);
        var openCounts = new java.util.HashMap<String, Integer>();
        Set<String> outageServices = new LinkedHashSet<>();
        for (Incident inc : recentAll) {
            if (inc.getStatus().isOpen() && inc.getAffectedService() != null) {
                String svcName = inc.getAffectedService().getName();
                int count = openCounts.merge(svcName, 1, Integer::sum);
                if (count >= OutageCorrelator.OUTAGE_THRESHOLD || inc.isOutageFlagged()) {
                    outageServices.add(svcName);
                }
            }
        }
        return outageServices;
    }

    private Incident loadIncidentOrThrow(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new IncidentNotFoundException(String.valueOf(incidentId));
        }
        return incidentRepository.findByIdWithAssociations(incidentId.trim().toUpperCase())
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private void ensureCanAccessIncident(Incident incident, User actor) {
        if (actor == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() == Role.EMPLOYEE) {
            if (!incident.getReporter().getUserCode().equalsIgnoreCase(actor.getUserCode())) {
                throw new UnauthorizedException("Employees can only access their own incidents.");
            }
            return;
        }
        if (actor.getRole() == Role.TECHNICIAN) {
            boolean isAssigned = incident.getAssignedTechnician() != null
                    && incident.getAssignedTechnician().getUserCode().equalsIgnoreCase(actor.getUserCode());
            boolean isEscalatedOwner = actor.getUserCode().equalsIgnoreCase(incident.getEscalatedToCode());
            boolean isLeadOfAssignedTeam = (actor instanceof TeamLead)
                    && incident.getAssignedTechnician() != null
                    && incident.getAssignedTechnician().getTeam() != null
                    && incident.getAssignedTechnician().getTeam().getTeamLead() != null
                    && incident.getAssignedTechnician().getTeam().getTeamLead().getUserCode()
                            .equalsIgnoreCase(actor.getUserCode());
            if (!isAssigned && !isEscalatedOwner && !isLeadOfAssignedTeam) {
                throw new UnauthorizedException("Technicians can only access assigned incidents.");
            }
        }
    }

    private Technician requireAssignedTechnicianOrAdmin(Incident incident, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return incident.getAssignedTechnician();
        }
        if (actor instanceof Technician tech
                && incident.getAssignedTechnician() != null
                && incident.getAssignedTechnician().getUserCode().equalsIgnoreCase(tech.getUserCode())) {
            return incident.getAssignedTechnician();
        }
        throw new UnauthorizedException("Only the assigned technician or an admin may perform this action.");
    }

    private void requireReporterOrAdmin(Incident incident, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (!incident.getReporter().getUserCode().equalsIgnoreCase(actor.getUserCode())) {
            throw new UnauthorizedException("Only the incident reporter or an administrator may perform this action.");
        }
    }

    private Incident saveIncident(Incident incident) {
        try {
            return incidentRepository.save(incident);
        } catch (DataAccessException ex) {
            throw new PersistenceFailureException("Failed to persist incident " + incident.getId(), ex);
        }
    }

    private Incident hydrateAssociations(Incident incident) {
        incident.getInvestigationNotes().size();
        incident.getInfoRequests().size();
        incident.getRelatedIncidentIds().size();
        incident.getDeclinedTechnicianCodes().size();
        if (incident.getAffectedService() != null) {
            incident.getAffectedService().getKeywords().size();
            incident.getAffectedService().getDependsOn().size();
        }
        if (incident.getCategory() != null) {
            incident.getCategory().getKeywords().size();
        }
        if (incident.getAssignedTechnician() != null) {
            incident.getAssignedTechnician().getSkills().size();
            incident.getAssignedTechnician().getServiceExperienceCounts().size();
            if (incident.getAssignedTechnician().getTeam() != null) {
                incident.getAssignedTechnician().getTeam().getName();
            }
        }
        return incident;
    }
}
