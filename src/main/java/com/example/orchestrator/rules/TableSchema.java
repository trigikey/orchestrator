package com.example.orchestrator.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* описание рабочей таблицы */
public final class TableSchema {

    private final String name;
    private final String category;
    private final List<String> files;
    private final List<ColumnRule> columns;
    private final Map<String, Integer> columnIndexes;

    public TableSchema(String name, String category, List<String> files, List<ColumnRule> columns) {
        this.name = name;
        this.category = category;
        this.files = List.copyOf(files);
        this.columns = List.copyOf(columns);
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < this.columns.size(); i++) {
            indexes.putIfAbsent(this.columns.get(i).column(), i);
        }
        this.columnIndexes = Map.copyOf(indexes);
    }

    public String name() {
        return name;
    }

    public String category() {
        return category;
    }

    public List<String> files() {
        return files;
    }

    public List<ColumnRule> columns() {
        return columns;
    }

    public int columnCount() {
        return columns.size();
    }

    public Map<String, Integer> columnIndexes() {
        return columnIndexes;
    }
}
