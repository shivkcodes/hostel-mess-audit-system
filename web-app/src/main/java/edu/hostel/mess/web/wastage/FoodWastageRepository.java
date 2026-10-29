package edu.hostel.mess.web.wastage;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only daily mess record queries and transparent consumption summaries. */
public final class FoodWastageRepository {
    private static final String BLOCK_OPTIONS_QUERY = """
            SELECT block_id, block_name
            FROM mess_blocks
            ORDER BY block_name, block_id
            """;
    private static final String DAILY_RECORDS_QUERY = """
            SELECT d.record_id,
                   d.block_id,
                   b.block_name,
                   d.record_date,
                   d.meals_served,
                   d.prepared_kg,
                   d.leftover_kg,
                   d.measurement_method,
                   d.notes,
                   d.created_at,
                   CASE
                       WHEN d.leftover_kg IS NOT NULL AND d.meals_served > 0
                       THEN ROUND(d.leftover_kg / NULLIF(d.meals_served, 0), 3)
                       ELSE NULL
                   END AS per_meal_leftover_kg
            FROM daily_mess_records d
            JOIN mess_blocks b ON b.block_id = d.block_id
            WHERE 1 = 1
            """;
    private static final String SUMMARY_SELECT = """
            COUNT(*) AS record_count,
            NVL(SUM(d.meals_served), 0) AS meals_served,
            COUNT(d.prepared_kg) AS prepared_record_count,
            CASE WHEN COUNT(d.prepared_kg) > 0 THEN SUM(d.prepared_kg) ELSE NULL END AS prepared_kg,
            COUNT(d.leftover_kg) AS measured_record_count,
            CASE WHEN COUNT(d.leftover_kg) > 0 THEN SUM(d.leftover_kg) ELSE NULL END AS measured_leftover_kg,
            COUNT(CASE WHEN d.leftover_kg IS NOT NULL AND d.meals_served > 0 THEN 1 END) AS per_meal_record_count,
            SUM(CASE WHEN d.leftover_kg IS NOT NULL AND d.meals_served > 0 THEN d.leftover_kg ELSE 0 END) AS per_meal_leftover_numerator,
            SUM(CASE WHEN d.leftover_kg IS NOT NULL AND d.meals_served > 0 THEN d.meals_served ELSE 0 END) AS per_meal_meals_denominator,
            CASE
                WHEN SUM(CASE WHEN d.leftover_kg IS NOT NULL AND d.meals_served > 0 THEN d.meals_served ELSE 0 END) > 0
                THEN ROUND(
                    SUM(CASE WHEN d.leftover_kg IS NOT NULL AND d.meals_served > 0 THEN d.leftover_kg ELSE 0 END)
                    / SUM(CASE WHEN d.leftover_kg IS NOT NULL AND d.meals_served > 0 THEN d.meals_served ELSE 0 END),
                    3)
                ELSE NULL
            END AS weighted_per_meal_kg
            """;
    private static final String SUMMARY_FROM = """
            FROM daily_mess_records d
            JOIN mess_blocks b ON b.block_id = d.block_id
            WHERE 1 = 1
            """;
    private static final int DAILY_RECORD_LIMIT = 100;

    public PageData loadPage(Connection connection, Filters filters) throws SQLException {
        return new PageData(loadBlocks(connection), loadSummary(connection, filters),
                loadBlockSummaries(connection, filters), loadDateSummaries(connection, filters),
                loadDailyRecords(connection, filters), filters);
    }

    private List<Map<String, Object>> loadBlocks(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(BLOCK_OPTIONS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("blockId", resultSet.getLong("block_id"));
                row.put("blockName", resultSet.getString("block_name"));
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<String, Object> loadSummary(Connection connection, Filters filters) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(SUMMARY_SELECT).append(SUMMARY_FROM);
        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, filters);
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Food wastage summary query returned no row.");
                }
                return readSummary(resultSet);
            }
        }
    }

    private List<Map<String, Object>> loadBlockSummaries(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT b.block_id, b.block_name, ")
                .append(SUMMARY_SELECT).append(SUMMARY_FROM);
        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, filters);
        sql.append(" GROUP BY b.block_id, b.block_name ORDER BY b.block_name, b.block_id");
        return loadSummaries(connection, sql.toString(), parameters, "block_id", "block_name");
    }

    private List<Map<String, Object>> loadDateSummaries(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT TRUNC(d.record_date) AS report_date, ")
                .append("COUNT(DISTINCT d.block_id) AS block_count, ")
                .append(SUMMARY_SELECT).append(SUMMARY_FROM);
        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, filters);
        sql.append(" GROUP BY TRUNC(d.record_date) ORDER BY report_date DESC");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = readSummary(resultSet);
                    Date reportDate = resultSet.getDate("report_date");
                    row.put("reportDate", reportDate);
                    row.put("blockCount", resultSet.getLong("block_count"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadSummaries(Connection connection, String sql,
            List<Object> parameters, String keyColumn, String nameColumn) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = readSummary(resultSet);
                    row.put("groupId", resultSet.getLong(keyColumn));
                    row.put("groupName", resultSet.getString(nameColumn));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadDailyRecords(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder(DAILY_RECORDS_QUERY);
        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, filters);
        sql.append(" ORDER BY d.record_date DESC, b.block_name, d.record_id DESC FETCH FIRST ")
                .append(DAILY_RECORD_LIMIT).append(" ROWS ONLY");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("recordId", resultSet.getLong("record_id"));
                    row.put("blockId", resultSet.getLong("block_id"));
                    row.put("blockName", resultSet.getString("block_name"));
                    row.put("recordDate", resultSet.getDate("record_date"));
                    row.put("mealsServed", resultSet.getLong("meals_served"));
                    row.put("preparedKg", resultSet.getBigDecimal("prepared_kg"));
                    row.put("leftoverKg", resultSet.getBigDecimal("leftover_kg"));
                    row.put("measurementMethod", resultSet.getString("measurement_method"));
                    row.put("notes", resultSet.getString("notes"));
                    Timestamp createdAt = resultSet.getTimestamp("created_at");
                    row.put("createdAt", createdAt == null ? null : new java.util.Date(createdAt.getTime()));
                    row.put("perMealLeftoverKg", resultSet.getBigDecimal("per_meal_leftover_kg"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private Map<String, Object> readSummary(ResultSet resultSet) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordCount", resultSet.getLong("record_count"));
        row.put("mealsServed", resultSet.getBigDecimal("meals_served"));
        row.put("preparedRecordCount", resultSet.getLong("prepared_record_count"));
        row.put("preparedKg", resultSet.getBigDecimal("prepared_kg"));
        row.put("measuredRecordCount", resultSet.getLong("measured_record_count"));
        row.put("measuredLeftoverKg", resultSet.getBigDecimal("measured_leftover_kg"));
        row.put("perMealRecordCount", resultSet.getLong("per_meal_record_count"));
        row.put("perMealLeftoverNumerator", resultSet.getBigDecimal("per_meal_leftover_numerator"));
        row.put("perMealMealsDenominator", resultSet.getBigDecimal("per_meal_meals_denominator"));
        row.put("weightedPerMealKg", resultSet.getBigDecimal("weighted_per_meal_kg"));
        return row;
    }

    private void appendFilters(StringBuilder sql, List<Object> parameters, Filters filters) {
        if (filters.fromDate() != null) {
            sql.append(" AND d.record_date >= ?");
            parameters.add(Date.valueOf(filters.fromDate()));
        }
        if (filters.toDate() != null) {
            sql.append(" AND d.record_date < ? + 1");
            parameters.add(Date.valueOf(filters.toDate()));
        }
        if (filters.blockId() != null) {
            sql.append(" AND d.block_id = ?");
            parameters.add(filters.blockId());
        }
    }

    private void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object parameter = parameters.get(index);
            if (parameter instanceof Date date) {
                statement.setDate(index + 1, date);
            } else if (parameter instanceof Long id) {
                statement.setLong(index + 1, id);
            } else {
                throw new SQLException("Unsupported food wastage filter parameter type.");
            }
        }
    }

    public record Filters(LocalDate fromDate, LocalDate toDate, Long blockId) {
    }

    public record PageData(List<Map<String, Object>> blocks,
            Map<String, Object> summary,
            List<Map<String, Object>> blockSummaries,
            List<Map<String, Object>> dateSummaries,
            List<Map<String, Object>> dailyRecords,
            Filters filters) {
        public PageData {
            blocks = List.copyOf(blocks);
            summary = Collections.unmodifiableMap(new LinkedHashMap<>(summary));
            blockSummaries = List.copyOf(blockSummaries);
            dateSummaries = List.copyOf(dateSummaries);
            dailyRecords = List.copyOf(dailyRecords);
        }
    }
}
