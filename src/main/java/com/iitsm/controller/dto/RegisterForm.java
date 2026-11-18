package com.iitsm.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-registration form DTO (creates Employee accounts only). */
public record RegisterForm(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50)
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        String phone,
        String department,
        String jobTitle
) {
    public static RegisterForm empty() {
        return new RegisterForm("", "", "", "", "", "IT", "Staff");
    }
}
