package com.iitsm.domain.model;

import com.iitsm.domain.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** End-user employee who submits, tracks, and verifies IT incidents. */
@Entity
@Table(name = "employees")
public class Employee extends User {

    @Column(length = 80)
    private String department;

    @Column(length = 80)
    private String jobTitle;

    /** Required by JPA. */
    protected Employee() {
    }

    /** Constructs a new Employee user account. */
    public Employee(String userCode, String username, String email, String fullName,
                    String passwordHash, String phone, String department, String jobTitle) {
        super(userCode, username, email, fullName, passwordHash, phone, Role.EMPLOYEE);
        setDepartment(department);
        setJobTitle(jobTitle);
    }

    @Override
    public String getDisplayIdentity() {
        return "Employee " + getUserCode();
    }

    public String getDepartment() {
        return department;
    }

    public final void setDepartment(String department) {
        this.department = department == null ? "General" : department.trim();
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public final void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle == null ? "Staff" : jobTitle.trim();
    }
}
