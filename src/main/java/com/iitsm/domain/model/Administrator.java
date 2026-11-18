package com.iitsm.domain.model;

import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.enums.Role;
import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Administrative user managing system configuration, users, policies, and escalations. */
@Entity
@Table(name = "administrators")
public class Administrator extends User {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminLevel adminLevel;

    /** Required by JPA. */
    protected Administrator() {
    }

    /** Constructs an Administrator account with explicit AdminLevel. */
    public Administrator(String userCode, String username, String email, String fullName,
                         String passwordHash, String phone, AdminLevel adminLevel) {
        super(userCode, username, email, fullName, passwordHash, phone, Role.ADMIN);
        setAdminLevel(adminLevel);
    }

    @Override
    public String getDisplayIdentity() {
        return adminLevel.name() + " " + getUserCode();
    }

    public AdminLevel getAdminLevel() {
        return adminLevel;
    }

    public final void setAdminLevel(AdminLevel adminLevel) {
        if (adminLevel == null) {
            throw new InvalidInputException("Admin level cannot be null.");
        }
        this.adminLevel = adminLevel;
    }
}
