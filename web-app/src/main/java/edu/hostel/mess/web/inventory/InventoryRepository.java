package edu.hostel.mess.web.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only queries for current inventory balances and their transaction history. */
public final class InventoryRepository {
    private static final String BLOCK_OPTIONS_QUERY = """
            SELECT block_id, block_name
            FROM mess_blocks
            ORDER BY block_name, block_id
            """;
    private static final String INGREDIENT_OPTIONS_QUERY = """
            SELECT ingredient_id, ingredient_name, base_unit
            FROM ingredients
            ORDER BY ingredient_name, ingredient_id
            """;
    private static final String STOCK_QUERY = """
            SELECT ib.block_id,
                   ib.ingredient_id,
                   b.block_name,
                   i.ingredient_name,
                   i.base_unit,
                   ib.on_hand_qty,
                   ib.reorder_level,
                   ib.updated_at,
                   CASE WHEN ib.on_hand_qty < ib.reorder_level THEN 'Y' ELSE 'N' END AS low_stock
            FROM inventory_balances ib
            JOIN mess_blocks b ON b.block_id = ib.block_id
            JOIN ingredients i ON i.ingredient_id = ib.ingredient_id
            WHERE 1 = 1
            """;
    private static final String TRANSACTION_QUERY = """
            SELECT it.transaction_id,
                   it.transaction_reference,
                   it.transaction_type,
                   it.quantity_delta,
                   it.balance_after_qty,
                   it.created_at,
                   b.block_name,
                   i.ingredient_name,
                   i.base_unit
            FROM inventory_transactions it
            JOIN mess_blocks b ON b.block_id = it.block_id
            JOIN ingredients i ON i.ingredient_id = it.ingredient_id
            WHERE 1 = 1
            """;
    private static final int TRANSACTION_LIMIT = 100;

    public PageData loadPage(Connection connection, Filters filters) throws SQLException {
        return new PageData(loadBlocks(connection), loadIngredients(connection),
                loadStockRows(connection, filters), loadTransactionRows(connection, filters), filters);
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

    private List<Map<String, Object>> loadIngredients(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(INGREDIENT_OPTIONS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ingredientId", resultSet.getLong("ingredient_id"));
                row.put("ingredientName", resultSet.getString("ingredient_name"));
                row.put("baseUnit", resultSet.getString("base_unit"));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadStockRows(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder(STOCK_QUERY);
        List<Object> parameters = new ArrayList<>();
        appendEntityFilters(sql, parameters, "ib", filters);
        sql.append(" ORDER BY b.block_name, i.ingredient_name, ib.block_id, ib.ingredient_id");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("blockId", resultSet.getLong("block_id"));
                    row.put("ingredientId", resultSet.getLong("ingredient_id"));
                    row.put("blockName", resultSet.getString("block_name"));
                    row.put("ingredientName", resultSet.getString("ingredient_name"));
                    row.put("baseUnit", resultSet.getString("base_unit"));
                    row.put("onHandQty", resultSet.getBigDecimal("on_hand_qty"));
                    row.put("reorderLevel", resultSet.getBigDecimal("reorder_level"));
                    Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                    row.put("updatedAt", updatedAt == null ? null : new java.util.Date(updatedAt.getTime()));
                    row.put("lowStock", "Y".equals(resultSet.getString("low_stock")));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadTransactionRows(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder(TRANSACTION_QUERY);
        List<Object> parameters = new ArrayList<>();
        appendEntityFilters(sql, parameters, "it", filters);
        if (filters.transactionType() != null) {
            sql.append(" AND it.transaction_type = ?");
            parameters.add(filters.transactionType());
        }
        sql.append(" ORDER BY it.created_at DESC, it.transaction_id DESC FETCH FIRST ")
                .append(TRANSACTION_LIMIT).append(" ROWS ONLY");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("transactionId", resultSet.getLong("transaction_id"));
                    row.put("transactionReference", resultSet.getString("transaction_reference"));
                    row.put("transactionType", resultSet.getString("transaction_type"));
                    row.put("quantityDelta", resultSet.getBigDecimal("quantity_delta"));
                    row.put("balanceAfterQty", resultSet.getBigDecimal("balance_after_qty"));
                    Timestamp createdAt = resultSet.getTimestamp("created_at");
                    row.put("createdAt", createdAt == null ? null : new java.util.Date(createdAt.getTime()));
                    row.put("blockName", resultSet.getString("block_name"));
                    row.put("ingredientName", resultSet.getString("ingredient_name"));
                    row.put("baseUnit", resultSet.getString("base_unit"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private void appendEntityFilters(StringBuilder sql, List<Object> parameters,
            String alias, Filters filters) {
        if (filters.blockId() != null) {
            sql.append(" AND ").append(alias).append(".block_id = ?");
            parameters.add(filters.blockId());
        }
        if (filters.ingredientId() != null) {
            sql.append(" AND ").append(alias).append(".ingredient_id = ?");
            parameters.add(filters.ingredientId());
        }
    }

    private void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object parameter = parameters.get(index);
            if (parameter instanceof Long id) {
                statement.setLong(index + 1, id);
            } else if (parameter instanceof String value) {
                statement.setString(index + 1, value);
            } else {
                throw new SQLException("Unsupported inventory filter parameter type.");
            }
        }
    }

    public record Filters(Long blockId, Long ingredientId, String transactionType) {
    }

    public record PageData(List<Map<String, Object>> blocks,
            List<Map<String, Object>> ingredients,
            List<Map<String, Object>> stockRows,
            List<Map<String, Object>> transactionRows,
            Filters filters) {
        public PageData {
            blocks = List.copyOf(blocks);
            ingredients = List.copyOf(ingredients);
            stockRows = List.copyOf(stockRows);
            transactionRows = List.copyOf(transactionRows);
        }
    }
}
