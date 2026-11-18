package com.iitsm.domain.model;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** IT service catalog entry with business criticality, SLA overrides, and upstream dependencies. */
@Entity
@Table(name = "services")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int businessCriticality;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_keywords", joinColumns = @JoinColumn(name = "service_id"))
    @Column(name = "keyword", nullable = false, length = 60)
    private Set<String> keywords = new LinkedHashSet<>();

    private Integer overrideResponseMins;

    private Integer overrideResolutionMins;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "service_dependencies",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_id")
    )
    private Set<ServiceEntity> dependsOn = new LinkedHashSet<>();

    /** Required by JPA. */
    protected ServiceEntity() {
    }

    /** Constructs a validated service catalog entity. */
    public ServiceEntity(String name, String description, int businessCriticality, Collection<String> keywords) {
        setName(name);
        setDescription(description);
        setBusinessCriticality(businessCriticality);
        setKeywords(keywords);
    }

    /** Adds an upstream dependency while preventing direct self-reference. */
    public void addDependency(ServiceEntity upstream) {
        if (upstream == null) {
            return;
        }
        if (this.equals(upstream)) {
            throw new InvalidInputException("A service cannot depend on itself: " + name);
        }
        dependsOn.add(upstream);
    }

    /** Replaces the dependency set safely. */
    public void setDependencies(Collection<ServiceEntity> dependencies) {
        this.dependsOn.clear();
        if (dependencies != null) {
            for (ServiceEntity dep : dependencies) {
                addDependency(dep);
            }
        }
    }

    /** Returns an unmodifiable view of upstream service dependencies. */
    public Set<ServiceEntity> getDependsOn() {
        return Collections.unmodifiableSet(dependsOn);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public final void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Service name cannot be blank.");
        }
        this.name = name.trim();
    }

    public String getDescription() {
        return description;
    }

    public final void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    public int getBusinessCriticality() {
        return businessCriticality;
    }

    public final void setBusinessCriticality(int businessCriticality) {
        if (businessCriticality < 1 || businessCriticality > 5) {
            throw new InvalidInputException("Business criticality must be between 1 and 5.");
        }
        this.businessCriticality = businessCriticality;
    }

    /** Returns an unmodifiable view of service matching keywords. */
    public Set<String> getKeywords() {
        return Collections.unmodifiableSet(keywords);
    }

    public final void setKeywords(Collection<String> keywords) {
        this.keywords.clear();
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw != null && !kw.isBlank()) {
                    this.keywords.add(kw.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    public Integer getOverrideResponseMins() {
        return overrideResponseMins;
    }

    public void setOverrideResponseMins(Integer overrideResponseMins) {
        if (overrideResponseMins != null && overrideResponseMins <= 0) {
            throw new InvalidInputException("Override response minutes must be positive.");
        }
        this.overrideResponseMins = overrideResponseMins;
    }

    public Integer getOverrideResolutionMins() {
        return overrideResolutionMins;
    }

    public void setOverrideResolutionMins(Integer overrideResolutionMins) {
        if (overrideResolutionMins != null && overrideResolutionMins <= 0) {
            throw new InvalidInputException("Override resolution minutes must be positive.");
        }
        this.overrideResolutionMins = overrideResolutionMins;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceEntity other)) return false;
        return Objects.equals(name == null ? null : name.toLowerCase(Locale.ROOT),
                other.name == null ? null : other.name.toLowerCase(Locale.ROOT));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name == null ? null : name.toLowerCase(Locale.ROOT));
    }
}
