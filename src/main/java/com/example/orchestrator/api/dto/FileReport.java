package com.example.orchestrator.api.dto;

import java.util.List;

public record FileReport(
        String fileName,
        String table,
        boolean recognized,
        String status,
        String message,
        int rows,
        int columns,
        int errorCount,
        int columnsWithErrors,
        List<ColumnReport> columnReports,
        List<String> warnings
) {
}
