package com.example.orchestrator.api.dto;

import java.util.List;

public record ColumnReport(String column, String rule, int checked, int errorCount, List<Issue> issues) {
}
