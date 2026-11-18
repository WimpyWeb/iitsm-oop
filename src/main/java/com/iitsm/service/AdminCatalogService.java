package com.iitsm.service;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.AppSetting;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.SlaViolation;
import com.iitsm.domain.model.Team;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.strategy.AssignmentStrategy;
import com.iitsm.domain.strategy.PriorityStrategy;
import com.iitsm.domain.strategy.SkillBasedAssignmentStrategy;
import com.iitsm.domain.strategy.WeightedScorePriorityStrategy;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.ServiceNotFoundException;
import com.iitsm.repository.AppSettingRepository;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ServiceRepository;
import com.iitsm.repository.SlaPolicyRepository;
import com.iitsm.repository.SlaViolationRepository;
import com.iitsm.repository.TeamLeadRepository;
import com.iitsm.repository.TeamRepository;
import com.iitsm.repository.TechnicianRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin catalog service managing categories, services, SLA policies, teams, and runtime strategies. */
@Service
@Transactional
public class AdminCatalogService {

    private final CategoryRepository categoryRepository;
    private final ServiceRepository serviceRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final SlaViolationRepository slaViolationRepository;
    private final TeamRepository teamRepository;
    private final TeamLeadRepository teamLeadRepository;
    private final TechnicianRepository technicianRepository;
    private final AppSettingRepository appSettingRepository;
    private final Map<String, PriorityStrategy> priorityStrategies = new LinkedHashMap<>();
    private final Map<String, AssignmentStrategy> assignmentStrategies = new LinkedHashMap<>();
    private final Priority defaultEscalationPriority;

    /** Constructs AdminCatalogService registering all available Priority and Assignment strategies. */
    public AdminCatalogService(CategoryRepository categoryRepository,
                               ServiceRepository serviceRepository,
                               SlaPolicyRepository slaPolicyRepository,
                               SlaViolationRepository slaViolationRepository,
                               TeamRepository teamRepository,
                               TeamLeadRepository teamLeadRepository,
                               TechnicianRepository technicianRepository,
                               AppSettingRepository appSettingRepository,
                               List<PriorityStrategy> priorityStrategyList,
                               List<AssignmentStrategy> assignmentStrategyList,
                               @Value("${app.escalation.min-priority:P1}") Priority defaultEscalationPriority) {
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.slaViolationRepository = slaViolationRepository;
        this.teamRepository = teamRepository;
        this.teamLeadRepository = teamLeadRepository;
        this.technicianRepository = technicianRepository;
        this.appSettingRepository = appSettingRepository;
        this.defaultEscalationPriority = defaultEscalationPriority == null ? Priority.P1 : defaultEscalationPriority;

        for (PriorityStrategy ps : priorityStrategyList) {
            priorityStrategies.put(ps.getName().toUpperCase(Locale.ROOT), ps);
        }
        for (AssignmentStrategy as : assignmentStrategyList) {
            assignmentStrategies.put(as.getName().toUpperCase(Locale.ROOT), as);
        }
    }

    /** Returns the currently configured PriorityStrategy persisted in AppSetting. */
    @Transactional(readOnly = true)
    public PriorityStrategy getActivePriorityStrategy() {
        String key = appSettingRepository.findById(AppSetting.KEY_PRIORITY_STRATEGY)
                .map(AppSetting::getSettingValue)
                .orElse(WeightedScorePriorityStrategy.STRATEGY_NAME)
                .toUpperCase(Locale.ROOT);
        PriorityStrategy strategy = priorityStrategies.get(key);
        if (strategy == null) {
            return priorityStrategies.values().iterator().next();
        }
        return strategy;
    }

    /** Switches and persists the active PriorityStrategy at runtime. */
    public void setActivePriorityStrategy(String strategyName) {
        String key = normalizeStrategyKey(strategyName);
        if (!priorityStrategies.containsKey(key)) {
            throw new InvalidInputException("Unknown priority strategy: " + strategyName);
        }
        saveSetting(AppSetting.KEY_PRIORITY_STRATEGY, key);
    }

    /** Returns the currently configured AssignmentStrategy persisted in AppSetting. */
    @Transactional(readOnly = true)
    public AssignmentStrategy getActiveAssignmentStrategy() {
        String key = appSettingRepository.findById(AppSetting.KEY_ASSIGNMENT_STRATEGY)
                .map(AppSetting::getSettingValue)
                .orElse(SkillBasedAssignmentStrategy.STRATEGY_NAME)
                .toUpperCase(Locale.ROOT);
        AssignmentStrategy strategy = assignmentStrategies.get(key);
        if (strategy == null) {
            return assignmentStrategies.values().iterator().next();
        }
        return strategy;
    }

    /** Switches and persists the active AssignmentStrategy at runtime. */
    public void setActiveAssignmentStrategy(String strategyName) {
        String key = normalizeStrategyKey(strategyName);
        if (!assignmentStrategies.containsKey(key)) {
            throw new InvalidInputException("Unknown assignment strategy: " + strategyName);
        }
        saveSetting(AppSetting.KEY_ASSIGNMENT_STRATEGY, key);
    }

    /** Returns minimum priority threshold eligible for automatic SLA escalation. */
    @Transactional(readOnly = true)
    public Priority getEscalationMinPriority() {
        return appSettingRepository.findById(AppSetting.KEY_ESCALATION_THRESHOLD)
                .map(s -> Priority.valueOf(s.getSettingValue().toUpperCase(Locale.ROOT)))
                .orElse(defaultEscalationPriority);
    }

    /** Updates minimum priority threshold eligible for automatic SLA escalation. */
    public void setEscalationMinPriority(Priority priority) {
        if (priority == null) {
            throw new InvalidInputException("Escalation priority threshold cannot be null.");
        }
        saveSetting(AppSetting.KEY_ESCALATION_THRESHOLD, priority.name());
    }

    /** Returns all registered PriorityStrategy keys. */
    public Set<String> getAvailablePriorityStrategies() {
        return Collections.unmodifiableSet(priorityStrategies.keySet());
    }

    /** Returns all registered AssignmentStrategy keys. */
    public Set<String> getAvailableAssignmentStrategies() {
        return Collections.unmodifiableSet(assignmentStrategies.keySet());
    }

    // ---- Category CRUD ----

    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Page<Category> listCategories(Pageable pageable) {
        return categoryRepository.findAllByOrderByNameAsc(pageable);
    }

    public Category createCategory(String name, Collection<String> keywords) {
        if (categoryRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new InvalidInputException("Category already exists: " + name);
        }
        return categoryRepository.save(new Category(name, keywords));
    }

    public Category updateCategory(Long id, String name, Collection<String> keywords) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new InvalidInputException("Category not found: " + id));
        cat.setName(name);
        cat.setKeywords(keywords);
        return categoryRepository.save(cat);
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    // ---- Service Catalog CRUD ----

    @Transactional(readOnly = true)
    public List<ServiceEntity> listServicesWithDependencies() {
        List<ServiceEntity> services = serviceRepository.findAllWithDependencies();
        for (ServiceEntity s : services) {
            s.getKeywords().size();
            s.getDependsOn().size();
        }
        return services;
    }

    @Transactional(readOnly = true)
    public Page<ServiceEntity> listServices(Pageable pageable) {
        return serviceRepository.findAllByOrderByNameAsc(pageable);
    }

    @Transactional(readOnly = true)
    public ServiceEntity getServiceWithDependencies(Long id) {
        ServiceEntity s = serviceRepository.findByIdWithDependencies(id)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + id));
        s.getKeywords().size();
        s.getDependsOn().size();
        return s;
    }

    public ServiceEntity createService(String name, String description, int criticality,
                                       Collection<String> keywords, Integer overrideResponseMins,
                                       Integer overrideResolutionMins, Collection<Long> dependsOnIds) {
        if (serviceRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new InvalidInputException("Service already exists: " + name);
        }
        ServiceEntity service = new ServiceEntity(name, description, criticality, keywords);
        service.setOverrideResponseMins(overrideResponseMins);
        service.setOverrideResolutionMins(overrideResolutionMins);
        applyDependencies(service, dependsOnIds);
        return serviceRepository.save(service);
    }

    public ServiceEntity updateService(Long id, String name, String description, int criticality,
                                       Collection<String> keywords, Integer overrideResponseMins,
                                       Integer overrideResolutionMins, Collection<Long> dependsOnIds) {
        ServiceEntity service = getServiceWithDependencies(id);
        service.setName(name);
        service.setDescription(description);
        service.setBusinessCriticality(criticality);
        service.setKeywords(keywords);
        service.setOverrideResponseMins(overrideResponseMins);
        service.setOverrideResolutionMins(overrideResolutionMins);
        applyDependencies(service, dependsOnIds);
        return serviceRepository.save(service);
    }

    private void applyDependencies(ServiceEntity service, Collection<Long> dependsOnIds) {
        if (dependsOnIds == null) {
            service.setDependencies(List.of());
            return;
        }
        List<ServiceEntity> deps = serviceRepository.findAllById(dependsOnIds);
        service.setDependencies(deps);
    }

    // ---- SLA Policy CRUD ----

    @Transactional(readOnly = true)
    public List<SlaPolicy> listSlaPolicies() {
        return slaPolicyRepository.findAllByOrderByPriorityAsc();
    }

    public SlaPolicy saveOrUpdateSlaPolicy(Priority priority, int responseMins, int resolutionMins) {
        SlaPolicy policy = slaPolicyRepository.findByPriority(priority)
                .orElseGet(() -> new SlaPolicy(priority, responseMins, resolutionMins));
        policy.setResponseMins(responseMins);
        policy.setResolutionMins(resolutionMins);
        return slaPolicyRepository.save(policy);
    }

    public void deleteSlaPolicy(Priority priority) {
        slaPolicyRepository.findByPriority(priority).ifPresent(slaPolicyRepository::delete);
    }

    // ---- Team CRUD ----

    @Transactional(readOnly = true)
    public List<Team> listTeamsWithMembers() {
        return teamRepository.findAllWithMembers();
    }

    public Team createTeam(String name, String specialtyCategory, String teamLeadCode, Collection<String> memberCodes) {
        if (teamRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new InvalidInputException("Team already exists: " + name);
        }
        TeamLead lead = null;
        if (teamLeadCode != null && !teamLeadCode.isBlank()) {
            lead = teamLeadRepository.findByUserCodeIgnoreCase(teamLeadCode.trim())
                    .orElseThrow(() -> new InvalidInputException("TeamLead not found: " + teamLeadCode));
        }
        Team team = teamRepository.save(new Team(name, specialtyCategory, lead));
        if (lead != null) {
            lead.setTeam(team);
            technicianRepository.save(lead);
        }
        if (memberCodes != null) {
            for (String code : memberCodes) {
                if (code != null && !code.isBlank()) {
                    Technician member = technicianRepository.findByUserCodeIgnoreCase(code.trim())
                            .orElseThrow(() -> new InvalidInputException("Technician not found: " + code));
                    team.addMember(member);
                    technicianRepository.save(member);
                }
            }
        }
        return teamRepository.save(team);
    }

    public Team assignMemberToTeam(Long teamId, String technicianCode) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new InvalidInputException("Team not found: " + teamId));
        Technician tech = technicianRepository.findByUserCodeIgnoreCase(technicianCode)
                .orElseThrow(() -> new InvalidInputException("Technician not found: " + technicianCode));
        team.addMember(tech);
        technicianRepository.save(tech);
        return teamRepository.save(team);
    }

    // ---- SLA Violations ----

    @Transactional(readOnly = true)
    public Page<SlaViolation> listSlaViolations(Pageable pageable) {
        return slaViolationRepository.findAllByOrderByDetectedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<SlaViolation> listSlaViolationsForIncident(String incidentId) {
        return slaViolationRepository.findByIncidentIdOrderByDetectedAtDesc(incidentId);
    }

    private String normalizeStrategyKey(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Strategy name cannot be blank.");
        }
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private void saveSetting(String key, String value) {
        AppSetting setting = appSettingRepository.findById(key)
                .orElseGet(() -> new AppSetting(key, value));
        setting.setSettingValue(value);
        appSettingRepository.save(setting);
    }
}
