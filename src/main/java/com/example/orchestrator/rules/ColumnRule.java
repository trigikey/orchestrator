package com.example.orchestrator.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Описание правил проверки одной колонки таблицы.
 *
 * <p>Правила задаются в {@link HardcodedRules} обычным Java-кодом. Никакие
 * внешние файлы с правилами во время работы приложения не читаются.</p>
 */
public final class ColumnRule {

    public enum Type {
        ANY,
        STRING,
        INTEGER,
        DECIMAL,
        DATE,
        DATETIME,
        TIME,
        BOOLEAN
    }

    /** Сравнение значения колонки со значением другой колонки. */
    public enum Compare {
        /** значение <= значение другой колонки */
        LE,
        /** значение >= значение другой колонки */
        GE
    }

    /** Специальные проверки, которые нельзя выразить простым диапазоном. */
    public enum Custom {
        NONE,
        KEY_ACCOUNT_INSTR,
        KEY_ACCOUNT_NO_RTS,
        TILL_MAT_TRDA,
        TILL_MAT_OPER,
        MID_FORMULA,
        ISIN_OR_CODE,
        DATE_SEQUENCE
    }

    public record Comparison(String otherColumn, Compare operation) {
    }

    public record BetweenColumns(String lowColumn, String highColumn) {
    }

    private final String column;
    private final String ruleText;
    private final Type type;

    private boolean required;
    private boolean blankOnly;
    private boolean unique;
    private boolean singleValue;
    private boolean dateRange;
    private boolean yyyymmdd;
    private boolean yyyymm;
    private boolean nonEmpty;

    private BigDecimal min;
    private BigDecimal max;
    private boolean minExclusive;
    private boolean maxExclusive;

    private Set<String> allowedValues;
    private Set<String> allowedTokens = Set.of();
    private String constantValue;
    private Pattern pattern;
    private String patternMessage;

    private final List<Comparison> comparisons = new ArrayList<>();
    private final List<BetweenColumns> betweenColumns = new ArrayList<>();
    private Custom custom = Custom.NONE;

    private ColumnRule(String column, String ruleText, Type type) {
        this.column = column;
        this.ruleText = ruleText == null ? "" : ruleText;
        this.type = type == null ? Type.ANY : type;
    }

    public static ColumnRule of(String column, String ruleText, Type type) {
        return new ColumnRule(column, ruleText, type);
    }

    public static ColumnRule string(String column, String ruleText) {
        return of(column, ruleText, Type.STRING);
    }

    public static ColumnRule integer(String column, String ruleText) {
        return of(column, ruleText, Type.INTEGER);
    }

    public static ColumnRule decimal(String column, String ruleText) {
        return of(column, ruleText, Type.DECIMAL);
    }

    public static ColumnRule date(String column, String ruleText) {
        return of(column, ruleText, Type.DATE);
    }

    public static ColumnRule dateTime(String column, String ruleText) {
        return of(column, ruleText, Type.DATETIME);
    }

    public static ColumnRule time(String column, String ruleText) {
        return of(column, ruleText, Type.TIME);
    }

    public static ColumnRule bool(String column, String ruleText) {
        return of(column, ruleText, Type.BOOLEAN);
    }

    public static ColumnRule any(String column, String ruleText) {
        return of(column, ruleText, Type.ANY);
    }

    public ColumnRule required() {
        required = true;
        return this;
    }

    public ColumnRule blankOnly() {
        blankOnly = true;
        return this;
    }

    public ColumnRule nonEmpty() {
        nonEmpty = true;
        return this;
    }

    public ColumnRule unique() {
        unique = true;
        return this;
    }

    public ColumnRule singleValue() {
        singleValue = true;
        return this;
    }

    public ColumnRule dateRange() {
        dateRange = true;
        return this;
    }

    public ColumnRule yyyymmdd() {
        yyyymmdd = true;
        return this;
    }

    public ColumnRule yyyymm() {
        yyyymm = true;
        return this;
    }

    public ColumnRule ge(String value) {
        min = new BigDecimal(value);
        minExclusive = false;
        return this;
    }

    public ColumnRule gt(String value) {
        min = new BigDecimal(value);
        minExclusive = true;
        return this;
    }

    public ColumnRule le(String value) {
        max = new BigDecimal(value);
        maxExclusive = false;
        return this;
    }

    public ColumnRule lt(String value) {
        max = new BigDecimal(value);
        maxExclusive = true;
        return this;
    }

    public ColumnRule between(String low, String high) {
        ge(low);
        le(high);
        return this;
    }

    public ColumnRule allowed(String... values) {
        allowedValues = new LinkedHashSet<>(Arrays.asList(values));
        return this;
    }

    public ColumnRule allowed(Set<String> values) {
        allowedValues = new LinkedHashSet<>(values);
        return this;
    }

    public ColumnRule allowToken(String token) {
        if (allowedTokens.isEmpty()) {
            allowedTokens = new LinkedHashSet<>();
        }
        allowedTokens.add(token);
        return this;
    }

    public ColumnRule constant(String value) {
        constantValue = value;
        return this;
    }

    public ColumnRule pattern(String regex, String message) {
        pattern = Pattern.compile(regex);
        patternMessage = message;
        return this;
    }

    public ColumnRule compare(String otherColumn, Compare operation) {
        comparisons.add(new Comparison(otherColumn, operation));
        return this;
    }

    public ColumnRule betweenColumns(String lowColumn, String highColumn) {
        betweenColumns.add(new BetweenColumns(lowColumn, highColumn));
        return this;
    }

    public ColumnRule custom(Custom custom) {
        this.custom = custom;
        return this;
    }

    public ColumnRule isin() {
        return pattern("^[A-Z]{2}[A-Z0-9]{9}[0-9]$",
                "Значение должно быть ISIN (12 символов)");
    }

    public ColumnRule lei() {
        return pattern("^[A-Z0-9]{20}$", "Значение должно быть LEI (20 символов)");
    }

    public ColumnRule mic() {
        return pattern("^[A-Z]{4}$", "Значение должно быть MIC (4 заглавные буквы)");
    }

    public ColumnRule uuid() {
        return pattern("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                "Значение должно быть UUID в формате 8-4-4-4-12");
    }

    public ColumnRule hex32() {
        return pattern("^[0-9a-fA-F]{32}$", "Значение должно быть 32 hex-символа");
    }

    public ColumnRule inn() {
        return pattern("^(\\d{10}|\\d{12})$", "Значение должно быть ИНН (10 или 12 цифр)");
    }

    public ColumnRule iso4217() {
        return pattern("^[A-Z]{3}$", "Значение должно быть кодом ISO 4217 (3 заглавные буквы)");
    }

    public ColumnRule countryCode() {
        return pattern("^[A-Z]{2}$", "Значение должно быть кодом ISO 3166-1 alpha-2");
    }

    public ColumnRule rtsPrefix() {
        return pattern("^RTS\\*.+$", "Значение должно начинаться с RTS* и быть непустым");
    }

    public String column() {
        return column;
    }

    public String ruleText() {
        return ruleText;
    }

    /**
     * Текст условий проверки без служебных частей «Тип»/«Обяз.»/«Уник.».
     * Именно он показывается в отчёте в поле {@code rule}.
     */
    public String conditionText() {
        int checksIndex = ruleText.indexOf("Проверки:");
        if (checksIndex < 0) {
            return ruleText;
        }
        String text = ruleText.substring(checksIndex + "Проверки:".length()).trim();
        int uniqueIndex = text.lastIndexOf("| Уник.:");
        if (uniqueIndex >= 0) {
            text = text.substring(0, uniqueIndex).trim();
        }
        return text;
    }

    public Type type() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isBlankOnly() {
        return blankOnly;
    }

    public boolean isUnique() {
        return unique;
    }

    public boolean isSingleValue() {
        return singleValue;
    }

    public boolean isDateRange() {
        return dateRange;
    }

    public boolean isYyyymmdd() {
        return yyyymmdd;
    }

    public boolean isYyyymm() {
        return yyyymm;
    }

    public boolean isNonEmpty() {
        return nonEmpty;
    }

    public BigDecimal min() {
        return min;
    }

    public BigDecimal max() {
        return max;
    }

    public boolean isMinExclusive() {
        return minExclusive;
    }

    public boolean isMaxExclusive() {
        return maxExclusive;
    }

    public Set<String> allowedValues() {
        return allowedValues;
    }

    public Set<String> allowedTokens() {
        return allowedTokens;
    }

    public String constantValue() {
        return constantValue;
    }

    public Pattern pattern() {
        return pattern;
    }

    public String patternMessage() {
        return patternMessage;
    }

    public List<Comparison> comparisons() {
        return comparisons;
    }

    public List<BetweenColumns> betweenColumns() {
        return betweenColumns;
    }

    public Custom custom() {
        return custom;
    }

    public boolean isNumeric() {
        return type == Type.INTEGER || type == Type.DECIMAL;
    }

    public boolean isTemporal() {
        return type == Type.DATE || type == Type.DATETIME;
    }

    public boolean isTime() {
        return type == Type.TIME;
    }
}
