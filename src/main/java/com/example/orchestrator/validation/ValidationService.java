package com.example.orchestrator.validation;

import com.example.orchestrator.api.dto.FileReport;
import com.example.orchestrator.api.dto.Summary;
import com.example.orchestrator.api.dto.ValidationResponse;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Принимает файлы и формирует общий отчёт. */
@Service
public class ValidationService {

    private final WorkbookValidator workbookValidator = new WorkbookValidator();

    public ValidationResponse validate(List<MultipartFile> files) {
        List<FileReport> reports = new ArrayList<>();
        int ok = 0;
        int withErrors = 0;
        int unrecognized = 0;
        int failed = 0;
        int totalIssues = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileReport report = validateFile(file);
            reports.add(report);
            totalIssues += report.errorCount();
            switch (report.status()) {
                case "OK" -> ok++;
                case "ERRORS" -> withErrors++;
                case "UNRECOGNIZED" -> unrecognized++;
                case "FAILED" -> failed++;
                default -> {
                    // неизвестный статус невозможен
                }
            }
        }

        Summary summary = new Summary(
                reports.size(),
                ok,
                withErrors,
                unrecognized,
                failed,
                totalIssues);

        return new ValidationResponse(reports, summary);
    }

    private FileReport validateFile(MultipartFile file) {
        String fileName = fileName(file);
        try (InputStream input = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(input)) {
            return workbookValidator.validate(fileName, workbook);
        } catch (Exception ex) {
            String detail = ex.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = ex.getClass().getSimpleName();
            }
            return new FileReport(
                    fileName,
                    "",
                    false,
                    "FAILED",
                    "Не удалось прочитать файл: " + detail,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of());
        }
    }

    private String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "без имени";
        }
        return name;
    }
}
