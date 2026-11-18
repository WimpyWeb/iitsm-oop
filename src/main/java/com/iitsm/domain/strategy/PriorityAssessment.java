package com.iitsm.domain.strategy;

import com.iitsm.domain.enums.Priority;

/** Immutable calculation result containing determined Priority and numeric score. */
public record PriorityAssessment(Priority priority, double score) {
}
