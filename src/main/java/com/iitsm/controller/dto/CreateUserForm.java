package com.iitsm.controller.dto;

import com.iitsm.domain.enums.AdminLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin form DTO for provisioning Employee, Technician, TeamLead, or Administrator accounts. */
public record CreateUserForm(
        @NotBlank String accountType,
        String userCode,
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        String phone,
        String skillsCsv,
        @Min(0) int experienceYears,
        @Min(1) int maxLoad,
        AdminLevel adminLevel,
        String department,
        String jobTitle
) {
}
