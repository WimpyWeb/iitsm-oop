package com.iitsm.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Form DTO for reporting a new IT incident. */
public record ReportIncidentForm(
        @NotNull(message = "Affected service is required")
        Long serviceId,

        Long categoryId,

        @NotBlank(message = "Description is required")
        @Size(min = 5, max = 4000, message = "Description must be between 5 and 4000 characters")
        String description,

        @Min(1) @Max(5)
        int impact,

        @Min(1) @Max(5)
        int urgency,

        @Min(0)
        int affectedUsersCount
) {
    public static ReportIncidentForm defaults() {
        return new ReportIncidentForm(null, null, "", 3, 3, 1);
    }
}
