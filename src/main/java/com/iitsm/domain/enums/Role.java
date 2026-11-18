package com.iitsm.domain.enums;

/** Application security roles mapped to Spring Security authorities. */
public enum Role {
    EMPLOYEE,
    TECHNICIAN,
    ADMIN;

    /** Returns the Spring Security authority string prefixed with ROLE_. */
    public String asAuthority() {
        return "ROLE_" + name();
    }
}
