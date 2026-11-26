package com.example.orchestrator.api.dto;

import java.util.List;

public record ValidationResponse(List<FileReport> files, Summary summary) {
}
