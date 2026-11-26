package com.example.orchestrator.validation;

import com.example.orchestrator.api.dto.Issue;
import com.example.orchestrator.rules.ColumnRule;

import java.util.ArrayList;
import java.util.List;

/* результат проверки одной колонки */
final class ColumnResult {

    static final int MAX_ISSUES = 10;
    static final int MAX_VALUE_LENGTH = 120;

    private final ColumnRule rule;
    private final List<Issue> issues = new ArrayList<>(MAX_ISSUES);
    private int checked;
    private int errorCount;

    ColumnResult(ColumnRule rule) {
        this.rule = rule;
    }

    ColumnRule rule() {
        return rule;
    }

    int checked() {
        return checked;
    }

    void checked(int checked) {
        this.checked = checked;
    }

    int errorCount() {
        return errorCount;
    }

    List<Issue> issues() {
        return issues;
    }

    void add(int row, String value, String message) {
        errorCount++;
        if (issues.size() < MAX_ISSUES) {
            issues.add(new Issue(row, truncate(value), message));
        }
    }

    void addMissing() {
        checked = 0;
        errorCount++;
        issues.add(new Issue(0, "", "Колонка отсутствует в файле"));
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH);
    }
}
