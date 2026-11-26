package com.example.orchestrator.validation;

import com.example.orchestrator.rules.TableSchema;

/* одна строка листа в порядке колонок схемы */
public final class RowData {

    private final String[] values;
    private final int rowNumber;
    private final TableSchema schema;

    public RowData(String[] values, int rowNumber, TableSchema schema) {
        this.values = values;
        this.rowNumber = rowNumber;
        this.schema = schema;
    }

    public String value(int schemaIndex) {
        if (schemaIndex < 0 || schemaIndex >= values.length) {
            return "";
        }
        String value = values[schemaIndex];
        return value == null ? "" : value;
    }

    public String value(String column) {
        Integer index = schema.columnIndexes().get(column);
        return index == null ? "" : value(index);
    }

    public int rowNumber() {
        return rowNumber;
    }
}
