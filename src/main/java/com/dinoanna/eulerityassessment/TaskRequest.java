package com.dinoanna.eulerityassessment;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TaskRequest(
        @NotBlank String title,
        @Nullable String description,
        @NotNull LocalDate dueDate,
        @NotNull Priority priority,
        @NotNull TaskStatus status
) {}
