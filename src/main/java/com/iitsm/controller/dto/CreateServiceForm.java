package com.iitsm.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** Admin form DTO for creating or updating catalog Services and upstream dependencies. */
public record CreateServiceForm(
        @NotBlank String name,
        String description,
        @Min(1) @Max(5) int businessCriticality,
        String keywordsCsv,
        Integer overrideResponseMins,
        Integer overrideResolutionMins,
        List<Long> dependsOnIds
) {
}
