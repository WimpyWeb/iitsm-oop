package com.iitsm.controller.dto;

import com.iitsm.domain.enums.Priority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Admin form DTO for configuring default SLA response and resolution deadlines. */
public record SlaPolicyForm(
        @NotNull Priority priority,
        @Min(1) int responseMins,
        @Min(1) int resolutionMins
) {
}
