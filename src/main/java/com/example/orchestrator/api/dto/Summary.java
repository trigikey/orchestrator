package com.example.orchestrator.api.dto;

public record Summary(
        int tables,
        int ok,
        int withErrors,
        int unrecognized,
        int failed,
        int totalIssues) {
}
