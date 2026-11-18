package com.iitsm.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Form DTO for editing an open incident prior to resolution. */
public record EditIncidentForm(
        @NotBlank(message = "Description is required")
        @Size(min = 5, max = 4000)
        String description,

        @Min(1) @Max(5)
        int impact,

        @Min(1) @Max(5)
        int urgency,

        @Min(0)
        int affectedUsersCount
) {
}
