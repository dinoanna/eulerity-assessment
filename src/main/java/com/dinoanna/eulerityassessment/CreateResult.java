package com.dinoanna.eulerityassessment;

import java.util.List;

import org.jspecify.annotations.Nullable;

public sealed interface CreateResult {
    record Saved(Task task) implements CreateResult {}
    record NeedsPreview(List<@Nullable Long> candidateIds) implements CreateResult {}
}
