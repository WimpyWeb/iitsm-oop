package com.iitsm.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** REST API request DTO for creating a new incident. */
public record CreateIncidentApiRequest(
        @NotNull Long serviceId,
        Long categoryId,
        @NotBlank @Size(min = 5, max = 4000) String description,
        @Min(1) @Max(5) int impact,
        @Min(1) @Max(5) int urgency,
        @Min(0) int affectedUsersCount
) {
}
