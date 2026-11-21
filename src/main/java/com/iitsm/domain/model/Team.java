package com.iitsm.domain.model;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Aggregates technicians into a specialized support team led by a TeamLead. */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 80)
    private String specialtyCategory;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_lead_id")
    private TeamLead teamLead;

    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY)
    private List<Technician> members = new ArrayList<>();

    /** Required by JPA. */
    protected Team() {
    }

    /** Constructs a support team with name, specialty category, and optional TeamLead. */
    public Team(String name, String specialtyCategory, TeamLead teamLead) {
        setName(name);
        setSpecialtyCategory(specialtyCategory);
        setTeamLead(teamLead);
    }

    /** Adds a technician to this team and links both sides of the aggregation. */
    public void addMember(Technician technician) {
        if (technician == null) {
            throw new InvalidInputException("Technician cannot be null.");
        }
        if (!members.contains(technician)) {
            members.add(technician);
        }
        technician.setTeam(this);
    }

    /** Removes a technician from this team. */
    public void removeMember(Technician technician) {
        if (technician != null) {
            members.remove(technician);
            if (this.equals(technician.getTeam())) {
                technician.setTeam(null);
            }
        }
    }

    /** Returns an unmodifiable view of team members. */
    public List<Technician> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Team name cannot be blank.");
        }
        this.name = name.trim();
    }

    public String getSpecialtyCategory() {
        return specialtyCategory;
    }

    public void setSpecialtyCategory(String specialtyCategory) {
        this.specialtyCategory = specialtyCategory == null ? "" : specialtyCategory.trim();
    }

    public TeamLead getTeamLead() {
        return teamLead;
    }

    public void setTeamLead(TeamLead teamLead) {
        this.teamLead = teamLead;
        if (teamLead != null) {
            addMember(teamLead);
        }
    }
}
