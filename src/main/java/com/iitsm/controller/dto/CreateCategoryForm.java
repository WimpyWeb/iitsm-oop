package com.iitsm.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** Admin form DTO for creating incident classification categories. */
public record CreateCategoryForm(
        @NotBlank String name,
        String keywordsCsv
) {
}
