package com.iitsm.controller.dto;

import com.iitsm.domain.enums.Priority;
import jakarta.validation.constraints.NotBlank;

/** REST API request DTO for transitioning or acting upon an existing incident. */
public record TransitionIncidentApiRequest(
        @NotBlank String action,
        String summary,
        String steps,
        String noteOrReason,
        String technicianCode,
        Priority priority
) {
}
