package com.iitsm.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Collection;

/** Senior technician who leads a support team and receives first-level SLA escalations. */
@Entity
@Table(name = "team_leads")
public class TeamLead extends Technician {

    /** Required by JPA. */
    protected TeamLead() {
    }

    /** Constructs a TeamLead inheriting technician capabilities with elevated escalation role. */
    public TeamLead(String userCode, String username, String email, String fullName,
                    String passwordHash, String phone, Collection<String> skills,
                    int experienceYears, int maxLoad) {
        super(userCode, username, email, fullName, passwordHash, phone, skills, experienceYears, maxLoad);
    }

    @Override
    public String getDisplayIdentity() {
        return "TeamLead " + getUserCode();
    }
}
