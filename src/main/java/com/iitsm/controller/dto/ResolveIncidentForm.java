package com.iitsm.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** Form DTO for submitting technician incident resolution details. */
public record ResolveIncidentForm(
        @NotBlank(message = "Resolution summary is required")
        String summary,

        @NotBlank(message = "Resolution steps are required")
        String steps
) {
}
