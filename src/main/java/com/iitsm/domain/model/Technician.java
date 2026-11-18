package com.iitsm.domain.model;

import com.iitsm.domain.enums.Role;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.exception.WorkloadExceededException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Support technician who investigates, diagnoses, and resolves assigned incidents. */
@Entity
@Table(name = "technicians")
public class Technician extends User {

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "technician_skills", joinColumns = @JoinColumn(name = "technician_id"))
    @Column(name = "skill", nullable = false, length = 60)
    private Set<String> skills = new LinkedHashSet<>();

    @Column(nullable = false)
    private int experienceYears;

    @Column(nullable = false)
    private boolean available = true;

    @Column(nullable = false)
    private int maxLoad = 5;

    @Column(nullable = false)
    private int currentLoad = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "technician_service_exp", joinColumns = @JoinColumn(name = "technician_id"))
    @MapKeyColumn(name = "service_name", length = 80)
    @Column(name = "resolved_count", nullable = false)
    private Map<String, Integer> serviceExperienceCounts = new LinkedHashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    /** Required by JPA. */
    protected Technician() {
    }

    /** Constructs a validated Technician instance. */
    public Technician(String userCode, String username, String email, String fullName,
                      String passwordHash, String phone, Collection<String> skills,
                      int experienceYears, int maxLoad) {
        super(userCode, username, email, fullName, passwordHash, phone, Role.TECHNICIAN);
        setSkills(skills);
        setExperienceYears(experienceYears);
        setMaxLoad(maxLoad);
        this.available = true;
        this.currentLoad = 0;
    }

    @Override
    public String getDisplayIdentity() {
        return "Technician " + getUserCode();
    }

    /** Returns true if technician is active, available, and below maxLoad. */
    public boolean canAcceptLoad() {
        return isActive() && available && currentLoad < maxLoad;
    }

    /** Validates and increments active load when assigning an incident. */
    public void assignLoad() {
        if (!isActive() || !available) {
            throw new TechnicianUnavailableException("Technician " + getUserCode() + " is unavailable.");
        }
        if (currentLoad >= maxLoad) {
            throw new WorkloadExceededException(
                    "Technician " + getUserCode() + " has reached max workload (" + maxLoad + ").");
        }
        currentLoad++;
    }

    /** Increments load during reopen flows even if at capacity to maintain accurate tracking. */
    public void forceIncrementLoad() {
        currentLoad++;
    }

    /** Decrements active load safely when an incident is resolved, declined, or reassigned away. */
    public void releaseLoad() {
        if (currentLoad > 0) {
            currentLoad--;
        }
    }

    /** Records a completed resolution on a given service name to improve future recommendations. */
    public void incrementServiceExperience(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return;
        }
        String key = serviceName.trim();
        serviceExperienceCounts.merge(key, 1, Integer::sum);
    }

    /** Returns prior resolved incident count for the given service name. */
    public int getExperienceForService(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return 0;
        }
        String target = serviceName.trim().toLowerCase(Locale.ROOT);
        return serviceExperienceCounts.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals(target))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    /** Checks case-insensitively whether this technician holds a matching skill. */
    public boolean hasSkill(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        String target = keyword.trim().toLowerCase(Locale.ROOT);
        return skills.stream().anyMatch(s -> {
            String lower = s.toLowerCase(Locale.ROOT);
            return lower.equals(target) || lower.contains(target) || target.contains(lower);
        });
    }

    /** Returns an unmodifiable view of this technician's skills. */
    public Set<String> getSkills() {
        return Collections.unmodifiableSet(skills);
    }

    /** Replaces the technician's normalized skills set. */
    public final void setSkills(Collection<String> newSkills) {
        this.skills.clear();
        if (newSkills != null) {
            for (String skill : newSkills) {
                if (skill != null && !skill.isBlank()) {
                    this.skills.add(skill.trim());
                }
            }
        }
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public final void setExperienceYears(int experienceYears) {
        if (experienceYears < 0) {
            throw new InvalidInputException("Experience years cannot be negative.");
        }
        this.experienceYears = experienceYears;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getMaxLoad() {
        return maxLoad;
    }

    public final void setMaxLoad(int maxLoad) {
        if (maxLoad < 1) {
            throw new InvalidInputException("Technician maxLoad must be at least 1.");
        }
        this.maxLoad = maxLoad;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(int currentLoad) {
        if (currentLoad < 0) {
            throw new InvalidInputException("Current load cannot be negative.");
        }
        this.currentLoad = currentLoad;
    }

    /** Returns an unmodifiable view of resolved-incident counts per service. */
    public Map<String, Integer> getServiceExperienceCounts() {
        return Collections.unmodifiableMap(serviceExperienceCounts);
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}
