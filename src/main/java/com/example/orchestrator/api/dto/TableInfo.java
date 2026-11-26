package com.example.orchestrator.api.dto;

import java.util.List;

public record TableInfo(
        String name,
        String category,
        int columns,
        List<String> files
) {
}
