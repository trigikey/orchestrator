package com.example.orchestrator.api.dto;

public record Issue(
        int row,
        String value,
        String message
) {
}
