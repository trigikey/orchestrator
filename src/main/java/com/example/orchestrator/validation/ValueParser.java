package com.example.orchestrator.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/** Разбор строковых значений ячеек. */
public final class ValueParser {

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ROOT)
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    );

    private ValueParser() {
    }

    public static BigDecimal number(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static boolean isInteger(BigDecimal number) {
        return number != null && number.stripTrailingZeros().scale() <= 0;
    }

    public static Boolean bool(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1" -> Boolean.TRUE;
            case "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    public static LocalTime time(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException ignored) {
            // пробуем формат без долей секунды
        }
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT));
        } catch (DateTimeParseException ignored) {
            // Excel часто хранит время как дату-время; берём время из неё.
            LocalDateTime dateTime = dateTime(value);
            return dateTime == null ? null : dateTime.toLocalTime();
        }
    }

    public static LocalDate date(String raw) {
        LocalDateTime dateTime = dateTime(raw);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    public static LocalDateTime dateTime(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        // ISO-формат с датой и временем, в том числе с долями секунды.
        String iso = value.indexOf(' ') > 0 ? value.replace(' ', 'T') : value;
        try {
            return LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // пробуем другие форматы
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // пробуем следующий формат
            }
        }

        // Дата без времени.
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // пробуем следующий формат
            }
        }

        // Компактные форматы: YYYYMMDD и YYYYMM.
        if (value.matches("\\d{8}")) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        if (value.matches("\\d{6}")) {
            try {
                YearMonth yearMonth = YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT));
                return yearMonth.atDay(1).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }

        // Дата-время с зоной/смещением.
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static boolean validYearMonth(String raw) {
        if (raw == null || !raw.trim().matches("\\d{6}")) {
            return false;
        }
        int month = Integer.parseInt(raw.trim().substring(4, 6));
        return month >= 1 && month <= 12;
    }
}
