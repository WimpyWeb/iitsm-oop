package com.iitsm.domain.model;

import com.iitsm.domain.enums.InfoRequestStatus;
import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.state.IncidentState;
import com.iitsm.domain.state.IncidentStateFactory;
import com.iitsm.exception.InvalidIncidentException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Core incident domain entity delegating lifecycle transitions to the State pattern. */
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incident_status", columnList = "status"),
        @Index(name = "idx_incident_priority", columnList = "priority"),
        @Index(name = "idx_incident_service", columnList = "service_id"),
        @Index(name = "idx_incident_technician", columnList = "technician_id"),
        @Index(name = "idx_incident_created", columnList = "createdAt")
})
public class Incident {

    @Id
    @Column(length = 20)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Employee reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity affectedService;

    @Column(nullable = false)
    private int impact;

    @Column(nullable = false)
    private int urgency;

    @Column(nullable = false)
    private int affectedUsersCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Column(nullable = false)
    private double priorityScore;

    @Column(nullable = false)
    private boolean priorityOverridden = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant respondedAt;

    private Instant resolvedAt;

    private Instant lastEscalatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    private Technician assignedTechnician;

    @Embedded
    private SlaInfo sla = new SlaInfo();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "incident_notes", joinColumns = @JoinColumn(name = "incident_id"))
    @OrderBy("createdAt ASC")
    private List<InvestigationNote> investigationNotes = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("requestedAt ASC")
    private List<InfoRequest> infoRequests = new ArrayList<>();

    @Embedded
    private Resolution resolution;

    @Embedded
    private Closure closure;

    @Embedded
    private Feedback feedback;

    @Column(nullable = false)
    private int escalationLevel = 0;

    @Column(length = 40)
    private String escalatedToCode;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "incident_related", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "related_incident_id", length = 20)
    private Set<String> relatedIncidentIds = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "incident_declined_techs", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "technician_code", length = 20)
    private Set<String> declinedTechnicianCodes = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean outageFlagged = false;

    @Column(length = 300)
    private String rootCauseSuggestion;

    @Column(length = 80)
    private String suggestedCategoryName;

    /** Required by JPA. */
    protected Incident() {
    }

    /** Constructs a validated incident initially in NEW status. */
    public Incident(String id, Employee reporter, ServiceEntity affectedService,
                    String description, int impact, int urgency, int affectedUsersCount) {
        if (id == null || id.isBlank()) {
            throw new InvalidIncidentException("Incident ID cannot be blank.");
        }
        if (reporter == null) {
            throw new InvalidIncidentException("Incident reporter is required.");
        }
        if (affectedService == null) {
            throw new InvalidIncidentException("Affected service is required.");
        }
        this.id = id.trim().toUpperCase();
        this.reporter = reporter;
        this.affectedService = affectedService;
        setDescription(description);
        setImpact(impact);
        setUrgency(urgency);
        setAffectedUsersCount(affectedUsersCount);
        this.status = IncidentStatus.NEW;
        this.createdAt = Instant.now();
        this.sla = new SlaInfo();
    }

    /** Resolves current state object and delegates classification. */
    public void classify(Category category, Priority priority, double priorityScore) {
        currentState().classify(this, category, priority, priorityScore);
    }

    /** Resolves current state object and delegates technician assignment or reassignment. */
    public void assign(Technician technician) {
        currentState().assign(this, technician);
    }

    /** Resolves current state object and delegates technician assignment decline with reason. */
    public void declineAssignment(Technician technician, String reason) {
        currentState().decline(this, technician, reason);
    }

    /** Resolves current state object and delegates technician acknowledgement. */
    public void acknowledge(Technician actor) {
        currentState().acknowledge(this, actor);
    }

    /** Resolves current state object and delegates start of investigation. */
    public void startProgress(Technician actor) {
        currentState().startProgress(this, actor);
    }

    /** Resolves current state object and delegates requesting clarification from reporter. */
    public void requestInfo(String question, User requestedBy) {
        currentState().requestInfo(this, question, requestedBy);
    }

    /** Resolves current state object and delegates answering an info request. */
    public void provideInfo(Long infoRequestId, String answer, User responder) {
        currentState().provideInfo(this, infoRequestId, answer, responder);
    }

    /** Resolves current state object and delegates incident resolution. */
    public void resolve(String summary, String steps, User resolvedBy) {
        currentState().resolve(this, summary, steps, resolvedBy);
    }

    /** Resolves current state object and delegates employee fix verification. */
    public void verify(User verifier) {
        currentState().verify(this, verifier);
    }

    /** Resolves current state object and delegates reopening a resolved incident. */
    public void reopen(String reason, User actor) {
        currentState().reopen(this, reason, actor);
    }

    /** Resolves current state object and delegates closing a verified incident. */
    public void close(String closureNotes, User closedBy) {
        currentState().close(this, closureNotes, closedBy);
    }

    /** Returns the active State pattern handler for this incident's current status. */
    public IncidentState currentState() {
        return IncidentStateFactory.forStatus(this.status);
    }

    /** Updates editable incident fields while status is strictly before RESOLVED. */
    public void updateDetails(String description, int impact, int urgency, int affectedUsersCount) {
        if (!this.status.isBeforeResolved()) {
            throw new InvalidIncidentException("Cannot edit incident " + id + " in status " + status + ".");
        }
        setDescription(description);
        setImpact(impact);
        setUrgency(urgency);
        setAffectedUsersCount(affectedUsersCount);
    }

    /** Updates priority and score when recalculated or overridden by authorized users. */
    public void updatePriority(Priority newPriority, double newScore, boolean manualOverride) {
        if (newPriority == null) {
            throw new InvalidInputException("Priority cannot be null.");
        }
        this.priority = newPriority;
        this.priorityScore = newScore;
        this.priorityOverridden = manualOverride;
    }

    /** Appends an investigation note to the incident timeline. */
    public void addInvestigationNote(User author, String text) {
        if (this.status.isClosed()) {
            throw new InvalidIncidentException("Cannot add notes to closed incident " + id);
        }
        String code = author == null ? "SYSTEM" : author.getUserCode();
        String name = author == null ? "System" : author.getFullName();
        investigationNotes.add(new InvestigationNote(code, name, Instant.now(), text));
    }

    /** Links a related incident ID detected by similarity analysis. */
    public void addRelatedIncidentId(String relatedId) {
        if (relatedId != null && !relatedId.isBlank() && !relatedId.equalsIgnoreCase(this.id)) {
            this.relatedIncidentIds.add(relatedId.trim().toUpperCase());
        }
    }

    /** Records employee feedback after resolution or closure. */
    public void submitFeedback(int rating, String comment) {
        if (this.status != IncidentStatus.RESOLVED
                && this.status != IncidentStatus.VERIFIED
                && this.status != IncidentStatus.CLOSED) {
            throw new InvalidIncidentException("Feedback is only permitted once an incident is resolved.");
        }
        this.feedback = new Feedback(rating, comment);
    }

    /** Applies status transition strictly verified by the active State handler. */
    public void transitionStatus(IncidentStatus nextStatus, IncidentState stateProof) {
        if (stateProof == null || stateProof.getStatus() != this.status) {
            throw new InvalidStateTransitionException("State mismatch transitioning incident " + id);
        }
        this.status = nextStatus;
    }

    /** Applies classification fields strictly verified by NewState. */
    public void applyClassification(Category category, Priority priority, double score, IncidentState stateProof) {
        verifyStateProof(stateProof);
        if (category == null || priority == null) {
            throw new InvalidInputException("Category and priority are required for classification.");
        }
        this.category = category;
        this.priority = priority;
        this.priorityScore = score;
    }

    /** Applies technician assignment and adjusts technician loads atomically. */
    public void applyAssignment(Technician newTech, IncidentState stateProof) {
        verifyStateProof(stateProof);
        if (newTech == null) {
            throw new InvalidInputException("Assigned technician cannot be null.");
        }
        if (this.assignedTechnician != null && this.assignedTechnician.equals(newTech)) {
            return;
        }
        // Increment target technician load first so failure never corrupts previous technician load.
        newTech.assignLoad();
        if (this.assignedTechnician != null) {
            this.assignedTechnician.releaseLoad();
        }
        this.assignedTechnician = newTech;
    }

    /** Applies technician decline, clearing assignee and recording decline history. */
    public void applyDecline(Technician decliningTech, String reason, IncidentState stateProof) {
        verifyStateProof(stateProof);
        if (decliningTech == null) {
            throw new InvalidInputException("Declining technician cannot be null.");
        }
        if (this.assignedTechnician == null || !this.assignedTechnician.equals(decliningTech)) {
            throw new InvalidInputException("Only the currently assigned technician may decline " + id);
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("A decline reason is required.");
        }
        this.declinedTechnicianCodes.add(decliningTech.getUserCode());
        decliningTech.releaseLoad();
        this.assignedTechnician = null;
        this.investigationNotes.add(new InvestigationNote(
                decliningTech.getUserCode(), decliningTech.getFullName(), Instant.now(),
                "Declined assignment: " + reason.trim()));
    }

    /** Applies technician acknowledgement and records response SLA outcome. */
    public void applyAcknowledgement(Technician actor, IncidentState stateProof) {
        verifyStateProof(stateProof);
        if (this.respondedAt == null) {
            this.respondedAt = Instant.now();
        }
        if (this.sla != null && this.sla.getResponseDue() != null && this.sla.getResponseMet() == null) {
            this.sla.setResponseMet(!this.respondedAt.isAfter(this.sla.getResponseDue()));
        }
    }

    /** Adds an info request during IN_PROGRESS -> PENDING_INFO transition. */
    public void applyInfoRequest(String question, User requestedBy, IncidentState stateProof) {
        verifyStateProof(stateProof);
        String requester = requestedBy == null ? "SYSTEM" : requestedBy.getDisplayIdentity();
        this.infoRequests.add(new InfoRequest(this, question, requester));
    }

    /** Answers a pending info request during PENDING_INFO -> IN_PROGRESS transition. */
    public void applyInfoAnswer(Long infoRequestId, String answer, User responder, IncidentState stateProof) {
        verifyStateProof(stateProof);
        InfoRequest target = null;
        if (infoRequestId != null) {
            target = infoRequests.stream()
                    .filter(r -> infoRequestId.equals(r.getId()))
                    .findFirst()
                    .orElse(null);
        }
        if (target == null) {
            target = infoRequests.stream()
                    .filter(r -> r.getStatus() == InfoRequestStatus.PENDING)
                    .findFirst()
                    .orElseThrow(() -> new InvalidInputException("No pending info request found on " + id));
        }
        target.recordAnswer(answer);
        String responderCode = responder == null ? reporter.getUserCode() : responder.getUserCode();
        String responderName = responder == null ? reporter.getFullName() : responder.getFullName();
        this.investigationNotes.add(new InvestigationNote(
                responderCode, responderName, Instant.now(),
                "Clarification provided: " + answer.trim()));
    }

    /** Applies resolution details, releases load, increments experience, and updates SLA. */
    public void applyResolution(String summary, String steps, User resolvedBy, IncidentState stateProof) {
        verifyStateProof(stateProof);
        String code = resolvedBy == null ? "SYSTEM" : resolvedBy.getUserCode();
        String name = resolvedBy == null ? "System" : resolvedBy.getFullName();
        this.resolution = new Resolution(summary, steps, code, name);
        this.resolvedAt = Instant.now();
        if (this.assignedTechnician != null) {
            this.assignedTechnician.releaseLoad();
            if (this.affectedService != null) {
                this.assignedTechnician.incrementServiceExperience(this.affectedService.getName());
            }
        }
        if (this.sla != null) {
            boolean resMet = this.sla.getResolutionDue() == null || !this.resolvedAt.isAfter(this.sla.getResolutionDue());
            this.sla.setResolutionMet(resMet);
            this.sla.evaluateStatus(this.createdAt, this.respondedAt, this.resolvedAt, this.resolvedAt, 80);
        }
    }

    /** Applies reopen logic during RESOLVED -> IN_PROGRESS transition. */
    public void applyReopen(String reason, User actor, IncidentState stateProof) {
        verifyStateProof(stateProof);
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("Reopen reason cannot be blank.");
        }
        this.resolvedAt = null;
        if (this.assignedTechnician != null) {
            this.assignedTechnician.forceIncrementLoad();
        }
        if (this.sla != null) {
            this.sla.setResolutionMet(null);
        }
        String code = actor == null ? reporter.getUserCode() : actor.getUserCode();
        String name = actor == null ? reporter.getFullName() : actor.getFullName();
        this.investigationNotes.add(new InvestigationNote(
                code, name, Instant.now(), "Reopened by reporter: " + reason.trim()));
    }

    /** Applies closure details during VERIFIED -> CLOSED transition. */
    public void applyClosure(String closureNotes, User closedBy, IncidentState stateProof) {
        verifyStateProof(stateProof);
        String code = closedBy == null ? "SYSTEM" : closedBy.getUserCode();
        this.closure = new Closure(Instant.now(), code, closureNotes);
    }

    private void verifyStateProof(IncidentState stateProof) {
        if (stateProof == null || stateProof.getStatus() != this.status) {
            throw new InvalidStateTransitionException("Unauthorized state mutation for incident " + id);
        }
    }

    public String getId() {
        return id;
    }

    public Employee getReporter() {
        return reporter;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public final void setDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidIncidentException("Incident description cannot be blank.");
        }
        this.description = description.trim();
    }

    public ServiceEntity getAffectedService() {
        return affectedService;
    }

    public void setAffectedService(ServiceEntity affectedService) {
        if (affectedService == null) {
            throw new InvalidIncidentException("Affected service cannot be null.");
        }
        this.affectedService = affectedService;
    }

    public int getImpact() {
        return impact;
    }

    public final void setImpact(int impact) {
        if (impact < 1 || impact > 5) {
            throw new InvalidInputException("Impact must be between 1 and 5.");
        }
        this.impact = impact;
    }

    public int getUrgency() {
        return urgency;
    }

    public final void setUrgency(int urgency) {
        if (urgency < 1 || urgency > 5) {
            throw new InvalidInputException("Urgency must be between 1 and 5.");
        }
        this.urgency = urgency;
    }

    public int getAffectedUsersCount() {
        return affectedUsersCount;
    }

    public final void setAffectedUsersCount(int affectedUsersCount) {
        if (affectedUsersCount < 0) {
            throw new InvalidInputException("Affected users count cannot be negative.");
        }
        this.affectedUsersCount = affectedUsersCount;
    }

    public Priority getPriority() {
        return priority;
    }

    /** Returns business-impact label (Critical, High, Medium, Low) for the assigned priority. */
    public String getBusinessImpactLabel() {
        return priority == null ? "Unclassified" : priority.getLabel();
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    public boolean isPriorityOverridden() {
        return priorityOverridden;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getLastEscalatedAt() {
        return lastEscalatedAt;
    }

    public void setLastEscalatedAt(Instant lastEscalatedAt) {
        this.lastEscalatedAt = lastEscalatedAt;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Technician getAssignedTechnician() {
        return assignedTechnician;
    }

    public SlaInfo getSla() {
        return sla;
    }

    public void setSla(SlaInfo sla) {
        this.sla = (sla == null) ? new SlaInfo() : sla;
    }

    /** Returns an unmodifiable view of investigation notes. */
    public List<InvestigationNote> getInvestigationNotes() {
        return Collections.unmodifiableList(investigationNotes);
    }

    /** Returns an unmodifiable view of clarification requests. */
    public List<InfoRequest> getInfoRequests() {
        return Collections.unmodifiableList(infoRequests);
    }

    public Resolution getResolution() {
        return resolution;
    }

    public Closure getClosure() {
        return closure;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public void setEscalationLevel(int escalationLevel) {
        if (escalationLevel < 0 || escalationLevel > 3) {
            throw new InvalidInputException("Escalation level must be between 0 and 3.");
        }
        this.escalationLevel = escalationLevel;
    }

    public String getEscalatedToCode() {
        return escalatedToCode;
    }

    public void setEscalatedToCode(String escalatedToCode) {
        this.escalatedToCode = escalatedToCode;
    }

    /** Returns an unmodifiable view of related incident IDs. */
    public Set<String> getRelatedIncidentIds() {
        return Collections.unmodifiableSet(relatedIncidentIds);
    }

    /** Returns an unmodifiable view of technician codes who declined this incident. */
    public Set<String> getDeclinedTechnicianCodes() {
        return Collections.unmodifiableSet(declinedTechnicianCodes);
    }

    public boolean isOutageFlagged() {
        return outageFlagged;
    }

    public void setOutageFlagged(boolean outageFlagged) {
        this.outageFlagged = outageFlagged;
    }

    public String getRootCauseSuggestion() {
        return rootCauseSuggestion;
    }

    public void setRootCauseSuggestion(String rootCauseSuggestion) {
        this.rootCauseSuggestion = rootCauseSuggestion;
    }

    public String getSuggestedCategoryName() {
        return suggestedCategoryName;
    }

    public void setSuggestedCategoryName(String suggestedCategoryName) {
        this.suggestedCategoryName = suggestedCategoryName;
    }
}
