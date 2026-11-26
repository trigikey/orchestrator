package com.example.orchestrator.validation;

import com.example.orchestrator.api.dto.ColumnReport;
import com.example.orchestrator.api.dto.FileReport;
import com.example.orchestrator.api.dto.Issue;
import com.example.orchestrator.rules.ColumnRule;
import com.example.orchestrator.rules.HardcodedRules;
import com.example.orchestrator.rules.TableSchema;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/* проверка одной таблички */
public final class WorkbookValidator {

    private static final int HEADER_SCAN_ROWS = 25;
    private static final BigDecimal MID_EPSILON = new BigDecimal("0.000001");
    private static final double MIN_HEADER_COVERAGE = 0.8;
    private static final int MIN_HEADER_MATCHES = 3;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter AMORTISATION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    public FileReport validate(String fileName, Workbook workbook) {
        DetectedTable detected = detect(workbook);
        if (detected == null) {
            return new FileReport(
                    fileName,
                    "",
                    false,
                    "UNRECOGNIZED",
                    "Не удалось определить таблицу: шапка файла не совпала ни с одной известной таблицей.",
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of());
        }
        return validateSheet(fileName, workbook, detected);
    }

    private FileReport validateSheet(String fileName, Workbook workbook, DetectedTable detected) {
        Sheet sheet = workbook.getSheetAt(detected.sheetIndex());
        TableSchema schema = detected.schema();
        List<ColumnRule> columns = schema.columns();

        Map<String, Integer> headerIndexes = new LinkedHashMap<>();
        for (HeaderCell header : detected.headers()) {
            headerIndexes.putIfAbsent(header.normalized(), header.index());
        }

        Set<String> schemaHeaders = new LinkedHashSet<>();
        for (ColumnRule column : columns) {
            schemaHeaders.add(normalizeHeader(column.column()));
        }

        Set<String> extraHeaders = new LinkedHashSet<>();
        for (HeaderCell header : detected.headers()) {
            if (!schemaHeaders.contains(header.normalized())) {
                extraHeaders.add(header.name());
            }
        }

        int[] cellIndexes = new int[columns.size()];
        boolean[] missing = new boolean[columns.size()];
        ColumnResult[] results = new ColumnResult[columns.size()];
        ColumnState[] states = new ColumnState[columns.size()];

        for (int i = 0; i < columns.size(); i++) {
            ColumnRule rule = columns.get(i);
            Integer cellIndex = headerIndexes.get(normalizeHeader(rule.column()));
            if (cellIndex == null) {
                cellIndexes[i] = -1;
                missing[i] = true;
            } else {
                cellIndexes[i] = cellIndex;
            }
            results[i] = new ColumnResult(rule);
            states[i] = new ColumnState();
        }

        int rows = 0;
        int headerRowIndex = detected.headerRowIndex();

        for (Row row : sheet) {
            if (row.getRowNum() <= headerRowIndex) {
                continue;
            }

            String[] values = new String[columns.size()];
            boolean nonEmptyRow = false;
            for (int i = 0; i < columns.size(); i++) {
                if (cellIndexes[i] < 0) {
                    values[i] = "";
                    continue;
                }
                Cell cell = row.getCell(cellIndexes[i]);
                String value = cellToString(cell);
                values[i] = value;
                if (!value.isBlank()) {
                    nonEmptyRow = true;
                }
            }

            if (!nonEmptyRow) {
                continue;
            }

            rows++;
            RowData rowData = new RowData(values, row.getRowNum() + 1, schema);
            for (int i = 0; i < columns.size(); i++) {
                if (missing[i]) {
                    continue;
                }
                validateCell(columns.get(i), values[i], rowData, results[i], states[i]);
            }
        }

        List<ColumnReport> columnReports = new ArrayList<>();
        int totalErrors = 0;
        int columnsWithErrors = 0;

        for (int i = 0; i < columns.size(); i++) {
            ColumnResult result = results[i];
            if (missing[i]) {
                result.addMissing();
            } else {
                result.checked(rows);
            }
            if (result.errorCount() > 0) {
                columnsWithErrors++;
                totalErrors += result.errorCount();
                columnReports.add(new ColumnReport(
                        result.rule().column(),
                        result.rule().conditionText(),
                        result.checked(),
                        result.errorCount(),
                        List.copyOf(result.issues())));
            }
        }

        List<String> warnings = new ArrayList<>();
        if (!extraHeaders.isEmpty()) {
            warnings.add("Лишние колонки, отсутствующие в правилах: " + String.join(", ", extraHeaders));
        }

        String status = totalErrors == 0 ? "OK" : "ERRORS";
        String message = totalErrors == 0
                ? "Ошибок не найдено"
                : errorsMessage(totalErrors, columnsWithErrors);

        return new FileReport(
                fileName,
                schema.name(),
                true,
                status,
                message,
                rows,
                schema.columnCount(),
                totalErrors,
                columnsWithErrors,
                columnReports,
                warnings);
    }

    private void validateCell(
            ColumnRule rule,
            String rawValue,
            RowData row,
            ColumnResult out,
            ColumnState state) {

        String value = rawValue == null ? "" : rawValue.trim();
        int rowNumber = row.rowNumber();

        if (value.isEmpty()) {
            if (rule.isRequired()) {
                out.add(rowNumber, "", "Значение обязательно для заполнения");
            }
            return;
        }

        if (rule.isBlankOnly()) {
            out.add(rowNumber, value, "Значение должно быть пустым");
            return;
        }

        if (rule.allowedTokens().contains(value)) {
            return;
        }

        String normalized = normalizeForCompare(value, rule);

        if (rule.isUnique()) {
            Integer firstRow = state.firstRows.putIfAbsent(normalized, rowNumber);
            if (firstRow != null) {
                out.add(rowNumber, value,
                        "Значение повторяется (первое вхождение: строка " + firstRow + ")");
            }
        }

        if (rule.isSingleValue()) {
            if (state.singleValue == null) {
                state.singleValue = normalized;
                state.singleRawValue = value;
                state.singleRow = rowNumber;
            } else if (!state.singleValue.equals(normalized)) {
                out.add(rowNumber, value,
                        "Все значения колонки должны совпадать (первое значение: '"
                                + state.singleRawValue + "', строка " + state.singleRow + ")");
            }
        }

        if (!typeValid(rule, value)) {
            out.add(rowNumber, value, typeMessage(rule.type()));
            return;
        }

        if (rule.constantValue() != null && !rule.constantValue().equals(value)) {
            out.add(rowNumber, value,
                    "Значение должно быть равно '" + rule.constantValue() + "'");
        }

        if (rule.allowedValues() != null && !isAllowed(rule, value)) {
            out.add(rowNumber, value,
                    "Недопустимое значение. Разрешены: " + String.join(", ", rule.allowedValues()));
        }

        if (rule.pattern() != null) {
            String patternValue = value;
            if (rule.type() == ColumnRule.Type.TIME) {
                var time = ValueParser.time(value);
                if (time != null) {
                    patternValue = time.format(TIME_FORMAT);
                }
            }
            if (!rule.pattern().matcher(patternValue).matches()) {
                out.add(rowNumber, value, rule.patternMessage());
            }
        }

        checkNumericRange(rule, value, rowNumber, out);
        checkDateRange(rule, value, rowNumber, out);
        checkCompactDate(rule, value, rowNumber, out);
        checkComparisons(rule, value, row, rowNumber, out);
        checkBetweenColumns(rule, value, row, rowNumber, out);
        checkCustom(rule, value, row, rowNumber, out);
    }

    private boolean isAllowed(ColumnRule rule, String value) {
        if (rule.type() == ColumnRule.Type.BOOLEAN) {
            for (String allowed : rule.allowedValues()) {
                if (allowed.equalsIgnoreCase(value)) {
                    return true;
                }
            }
            return false;
        }
        return rule.allowedValues().contains(value);
    }

    private void checkNumericRange(ColumnRule rule, String value, int rowNumber, ColumnResult out) {
        if (rule.min() == null && rule.max() == null) {
            return;
        }
        BigDecimal number = ValueParser.number(value);
        if (number == null) {
            return;
        }
        if (rule.min() != null) {
            int comparison = number.compareTo(rule.min());
            if (rule.isMinExclusive() ? comparison <= 0 : comparison < 0) {
                out.add(rowNumber, value,
                        "Значение должно быть " + (rule.isMinExclusive() ? ">" : ">=")
                                + " " + display(rule.min()));
            }
        }
        if (rule.max() != null) {
            int comparison = number.compareTo(rule.max());
            if (rule.isMaxExclusive() ? comparison >= 0 : comparison > 0) {
                out.add(rowNumber, value,
                        "Значение должно быть " + (rule.isMaxExclusive() ? "<" : "<=")
                                + " " + display(rule.max()));
            }
        }
    }

    private void checkDateRange(ColumnRule rule, String value, int rowNumber, ColumnResult out) {
        if (!rule.isDateRange()) {
            return;
        }
        LocalDateTime dateTime = ValueParser.dateTime(value);
        if (dateTime == null) {
            return;
        }
        int year = dateTime.getYear();
        if (year < 1900 || year > 2100) {
            out.add(rowNumber, value,
                    "Значение должно быть в диапазоне дат 1900-01-01..2100-12-31");
        }
    }

    private void checkCompactDate(ColumnRule rule, String value, int rowNumber, ColumnResult out) {
        if (rule.isYyyymmdd()) {
            if (!value.matches("\\d{8}") || ValueParser.date(value) == null) {
                out.add(rowNumber, value,
                        "Значение должно быть корректной датой в формате YYYYMMDD");
            }
        }
        if (rule.isYyyymm() && !ValueParser.validYearMonth(value)) {
            out.add(rowNumber, value, "Значение должно быть в формате YYYYMM");
        }
    }

    private void checkComparisons(
            ColumnRule rule,
            String value,
            RowData row,
            int rowNumber,
            ColumnResult out) {

        for (ColumnRule.Comparison comparison : rule.comparisons()) {
            String otherValue = row.value(comparison.otherColumn());
            if (otherValue.isBlank()) {
                continue;
            }

            int result;
            if (rule.isTemporal()) {
                LocalDate left = ValueParser.date(value);
                LocalDate right = ValueParser.date(otherValue);
                if (left == null || right == null) {
                    continue;
                }
                result = left.compareTo(right);
            } else if (rule.isNumeric()) {
                BigDecimal left = ValueParser.number(value);
                BigDecimal right = ValueParser.number(otherValue);
                if (left == null || right == null) {
                    continue;
                }
                result = left.compareTo(right);
            } else {
                result = value.compareTo(otherValue);
            }

            boolean failed = switch (comparison.operation()) {
                case LE -> result > 0;
                case GE -> result < 0;
            };
            if (failed) {
                String sign = comparison.operation() == ColumnRule.Compare.LE ? "<=" : ">=";
                out.add(rowNumber, value,
                        "Значение должно быть " + sign + " значению колонки '"
                                + comparison.otherColumn() + "'");
            }
        }
    }

    private void checkBetweenColumns(
            ColumnRule rule,
            String value,
            RowData row,
            int rowNumber,
            ColumnResult out) {

        for (ColumnRule.BetweenColumns range : rule.betweenColumns()) {
            String lowValue = row.value(range.lowColumn());
            String highValue = row.value(range.highColumn());
            if (lowValue.isBlank() || highValue.isBlank()) {
                continue;
            }

            int below;
            int above;
            if (rule.isNumeric()) {
                BigDecimal current = ValueParser.number(value);
                BigDecimal low = ValueParser.number(lowValue);
                BigDecimal high = ValueParser.number(highValue);
                if (current == null || low == null || high == null) {
                    continue;
                }
                below = current.compareTo(low);
                above = current.compareTo(high);
            } else if (rule.isTemporal()) {
                LocalDate current = ValueParser.date(value);
                LocalDate low = ValueParser.date(lowValue);
                LocalDate high = ValueParser.date(highValue);
                if (current == null || low == null || high == null) {
                    continue;
                }
                below = current.compareTo(low);
                above = current.compareTo(high);
            } else {
                below = value.compareTo(lowValue);
                above = value.compareTo(highValue);
            }

            if (below < 0 || above > 0) {
                out.add(rowNumber, value,
                        "Значение должно быть между значениями колонок '"
                                + range.lowColumn() + "' и '" + range.highColumn() + "'");
            }
        }
    }

    private void checkCustom(
            ColumnRule rule,
            String value,
            RowData row,
            int rowNumber,
            ColumnResult out) {

        switch (rule.custom()) {
            case KEY_ACCOUNT_INSTR -> {
                String account = row.value("account");
                String instrument = row.value("instr_short");
                if (!account.isBlank() && !instrument.isBlank()) {
                    String prefix = account + instrument;
                    if (!value.startsWith(prefix) || value.length() <= prefix.length()) {
                        out.add(rowNumber, value,
                                "Значение должно начинаться с account + instr_short и быть длиннее");
                    }
                }
            }
            case KEY_ACCOUNT_NO_RTS -> {
                String account = row.value("account");
                if (!account.isBlank()) {
                    String expected = account.startsWith("RTS*") ? account.substring(4) : account;
                    if (!value.equals(expected)) {
                        out.add(rowNumber, value,
                                "Значение должно совпадать с account без префикса RTS*");
                    }
                }
            }
            case TILL_MAT_TRDA -> checkTillDays(value, row, "mat_date", "trda_date", rowNumber, out);
            case TILL_MAT_OPER -> checkTillDays(value, row, "mat_date", "operdate", rowNumber, out);
            case MID_FORMULA -> {
                BigDecimal ask = ValueParser.number(row.value("ask"));
                BigDecimal bid = ValueParser.number(row.value("bid"));
                BigDecimal mid = ValueParser.number(value);
                if (ask != null && bid != null && mid != null) {
                    BigDecimal expected = ask.add(bid).divide(BigDecimal.valueOf(2), MathContext.DECIMAL128);
                    if (mid.subtract(expected).abs().compareTo(MID_EPSILON) > 0) {
                        out.add(rowNumber, value,
                                "Значение должно быть равно (ask + bid) / 2");
                    }
                }
            }
            case ISIN_OR_CODE -> {
                // Допускается ISIN, внутренний код или код VTB; достаточно непустого значения.
            }
            case DATE_SEQUENCE -> validateAmortisationSchedule(value, rowNumber, out);
            case NONE -> {
                // нет дополнительных проверок
            }
        }
    }

    private void validateAmortisationSchedule(String value, int rowNumber, ColumnResult out) {
        String[] parts = value.split("\\\\");
        for (String part : parts) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int equals = item.lastIndexOf('=');
            if (equals <= 0 || equals == item.length() - 1) {
                out.add(rowNumber, value,
                        "Значение должно быть последовательностью DD/MM/YYYY=сумма, разделитель \\");
                return;
            }
            String datePart = item.substring(0, equals).trim();
            String amountPart = item.substring(equals + 1).trim();
            try {
                LocalDate.parse(datePart, AMORTISATION_DATE_FORMAT);
            } catch (DateTimeParseException ex) {
                out.add(rowNumber, value,
                        "Значение должно быть последовательностью DD/MM/YYYY=сумма, разделитель \\");
                return;
            }
            if (ValueParser.number(amountPart) == null) {
                out.add(rowNumber, value,
                        "Значение должно быть последовательностью DD/MM/YYYY=сумма, разделитель \\");
                return;
            }
        }
    }

    private void checkTillDays(
            String value,
            RowData row,
            String dateColumn,
            String otherDateColumn,
            int rowNumber,
            ColumnResult out) {

        BigDecimal number = ValueParser.number(value);
        if (!ValueParser.isInteger(number)) {
            return;
        }
        LocalDate date = ValueParser.date(row.value(dateColumn));
        LocalDate otherDate = ValueParser.date(row.value(otherDateColumn));
        if (date == null || otherDate == null) {
            return;
        }
        long expected = ChronoUnit.DAYS.between(otherDate, date);
        long actual;
        try {
            actual = number.longValueExact();
        } catch (ArithmeticException ex) {
            return;
        }
        if (Math.abs(actual - expected) > 1) {
            out.add(rowNumber, value,
                    "Значение должно соответствовать разнице " + dateColumn + " и "
                            + otherDateColumn + " (допуск 1 день)");
        }
    }

    private boolean typeValid(ColumnRule rule, String value) {
        return switch (rule.type()) {
            case ANY, STRING -> true;
            case INTEGER -> ValueParser.isInteger(ValueParser.number(value));
            case DECIMAL -> ValueParser.number(value) != null;
            case DATE -> ValueParser.date(value) != null;
            case DATETIME -> ValueParser.dateTime(value) != null;
            case TIME -> ValueParser.time(value) != null;
            case BOOLEAN -> ValueParser.bool(value) != null;
        };
    }

    private String typeMessage(ColumnRule.Type type) {
        return switch (type) {
            case INTEGER -> "Значение должно быть целым числом";
            case DECIMAL -> "Значение должно быть числом";
            case DATE -> "Значение должно быть датой";
            case DATETIME -> "Значение должно быть датой-временем";
            case TIME -> "Значение должно быть временем в формате HH:MM:SS";
            case BOOLEAN -> "Значение должно быть 0, 1, True или False";
            default -> "Некорректное значение";
        };
    }

    private String normalizeForCompare(String value, ColumnRule rule) {
        if (rule.isNumeric()) {
            BigDecimal number = ValueParser.number(value);
            if (number != null) {
                return number.stripTrailingZeros().toPlainString();
            }
        }
        if (rule.isTemporal()) {
            if (rule.type() == ColumnRule.Type.DATE) {
                LocalDate date = ValueParser.date(value);
                if (date != null) {
                    return date.toString();
                }
            } else {
                LocalDateTime dateTime = ValueParser.dateTime(value);
                if (dateTime != null) {
                    return dateTime.toString();
                }
            }
        }
        if (rule.isTime()) {
            var time = ValueParser.time(value);
            if (time != null) {
                return time.toString();
            }
        }
        if (rule.type() == ColumnRule.Type.BOOLEAN) {
            Boolean bool = ValueParser.bool(value);
            if (bool != null) {
                return bool.toString();
            }
        }
        return value;
    }

    private DetectedTable detect(Workbook workbook) {
        DetectedTable best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            int lastRow = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS - 1);
            for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                List<HeaderCell> headers = readHeaderCells(row);
                if (headers.isEmpty()) {
                    continue;
                }
                for (TableSchema schema : HardcodedRules.tables()) {
                    HeaderMatch match = match(headers, schema);
                    if (match == null) {
                        continue;
                    }
                    int score = match.matched() * 1000 - match.extra() * 10 - rowIndex;
                    if (score > bestScore) {
                        bestScore = score;
                        best = new DetectedTable(schema, sheetIndex, rowIndex, headers);
                    }
                }
            }
        }
        return best;
    }

    private HeaderMatch match(List<HeaderCell> headers, TableSchema schema) {
        if (headers.isEmpty() || schema.columnCount() == 0) {
            return null;
        }
        int position = 0;
        int matched = 0;
        for (ColumnRule column : schema.columns()) {
            String expected = normalizeHeader(column.column());
            for (int i = position; i < headers.size(); i++) {
                if (headers.get(i).normalized().equals(expected)) {
                    matched++;
                    position = i + 1;
                    break;
                }
            }
        }
        double coverage = matched / (double) schema.columnCount();
        if (matched < MIN_HEADER_MATCHES || coverage < MIN_HEADER_COVERAGE) {
            return null;
        }
        return new HeaderMatch(matched, Math.max(0, headers.size() - matched));
    }

    private List<HeaderCell> readHeaderCells(Row row) {
        List<HeaderCell> headers = new ArrayList<>();
        short firstCell = row.getFirstCellNum();
        short lastCell = row.getLastCellNum();
        if (firstCell < 0 || lastCell < 0) {
            return headers;
        }
        for (int index = firstCell; index < lastCell; index++) {
            Cell cell = row.getCell(index);
            String name = cellToString(cell).replace("\uFEFF", "").trim();
            if (!name.isEmpty()) {
                headers.add(new HeaderCell(index, name, normalizeHeader(name)));
            }
        }
        return headers;
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String cellToString(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
                    yield dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue())
                        .stripTrailingZeros()
                        .toPlainString();
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? "true" : "false";
            case ERROR -> FormulaError.forInt(cell.getErrorCellValue()).getString();
            default -> "";
        };
    }

    private static String display(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String errorsMessage(int errors, int columns) {
        int lastDigit = columns % 10;
        int lastTwo = columns % 100;
        String word = lastDigit == 1 && lastTwo != 11 ? "колонке" : "колонках";
        return "Найдены ошибки: " + errors + " в " + columns + " " + word;
    }

    private record HeaderCell(int index, String name, String normalized) {
    }

    private record HeaderMatch(int matched, int extra) {
    }

    private record DetectedTable(
            TableSchema schema,
            int sheetIndex,
            int headerRowIndex,
            List<HeaderCell> headers) {
    }

    private static final class ColumnState {
        private final Map<String, Integer> firstRows = new HashMap<>();
        private String singleValue;
        private String singleRawValue;
        private int singleRow;
    }
}
