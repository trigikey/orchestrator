package com.example.orchestrator.api;

import com.example.orchestrator.api.dto.HealthResponse;
import com.example.orchestrator.api.dto.TableInfo;
import com.example.orchestrator.api.dto.ValidationResponse;
import com.example.orchestrator.rules.HardcodedRules;
import com.example.orchestrator.rules.TableSchema;
import com.example.orchestrator.validation.ValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ValidationService validationService;

    public ApiController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return HealthResponse.ok();
    }

    @GetMapping("/tables")
    public List<TableInfo> tables() {
        return HardcodedRules.tables().stream().map(this::toTableInfo).toList();
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ValidationResponse validate(@RequestParam("files") List<MultipartFile> files) {
        List<MultipartFile> nonEmpty = files.stream().filter(file -> file != null && !file.isEmpty()).toList();

        if (nonEmpty.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        return validationService.validate(nonEmpty);
    }

    private TableInfo toTableInfo(TableSchema schema) {
        return new TableInfo(
                schema.name(),
                schema.category(),
                schema.columnCount(),
                schema.files());
    }
}
