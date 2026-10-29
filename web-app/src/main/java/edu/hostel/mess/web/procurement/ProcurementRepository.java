package edu.hostel.mess.web.procurement;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.hostel.mess.web.db.OracleConnectionFactory;

/** Database queries and the atomic purchase-order creation operation. */
public final class ProcurementRepository {
    private static final String VENDORS_QUERY = """
            SELECT vendor_id, vendor_code, vendor_name
            FROM vendors
            WHERE active_flag = 'Y'
            ORDER BY vendor_name, vendor_code
            """;

    private static final String BLOCKS_QUERY = """
            SELECT block_id, block_code, block_name
            FROM mess_blocks
            WHERE active_flag = 'Y'
            ORDER BY block_name, block_code
            """;

    private static final String INGREDIENTS_QUERY = """
            SELECT ingredient_id, ingredient_code, ingredient_name, base_unit
            FROM ingredients
            WHERE active_flag = 'Y'
            ORDER BY ingredient_name, ingredient_code
            """;

    private static final String PURCHASE_ORDERS_QUERY = """
            SELECT po.po_id,
                   po.po_number,
                   v.vendor_name,
                   b.block_name,
                   po.ordered_at,
                   po.expected_by,
                   po.status,
                   po.approved_amount,
                   COUNT(poi.po_item_id) AS item_count,
                   NVL(SUM(poi.ordered_qty * poi.unit_price), 0) AS order_value
            FROM purchase_orders po
            JOIN vendors v ON v.vendor_id = po.vendor_id
            JOIN mess_blocks b ON b.block_id = po.block_id
            LEFT JOIN purchase_order_items poi ON poi.po_id = po.po_id
            GROUP BY po.po_id, po.po_number, v.vendor_name, b.block_name,
                     po.ordered_at, po.expected_by, po.status, po.approved_amount
            ORDER BY po.ordered_at DESC, po.po_id DESC
            """;

    private static final String ACTIVITY_QUERY = """
            SELECT TRUNC(po.ordered_at, 'MM') AS activity_month,
                   i.base_unit,
                   SUM(poi.ordered_qty) AS ordered_qty
            FROM purchase_orders po
            JOIN purchase_order_items poi ON poi.po_id = po.po_id
            JOIN ingredients i ON i.ingredient_id = poi.ingredient_id
            WHERE po.status <> 'CANCELLED'
            """;

    private static final String ACTIVE_VENDOR_QUERY =
            "SELECT 1 FROM vendors WHERE vendor_id = ? AND active_flag = 'Y'";
    private static final String ACTIVE_BLOCK_QUERY =
            "SELECT 1 FROM mess_blocks WHERE block_id = ? AND active_flag = 'Y'";
    private static final String ACTIVE_INGREDIENT_QUERY =
            "SELECT 1 FROM ingredients WHERE ingredient_id = ? AND active_flag = 'Y'";
    private static final String DUPLICATE_PO_QUERY =
            "SELECT 1 FROM purchase_orders WHERE po_number = ?";

    public PageData loadPage(Connection connection) throws SQLException {
        return new PageData(loadVendors(connection), loadBlocks(connection),
                loadIngredients(connection), loadPurchaseOrders(connection));
    }

    /** Monthly ordered quantities grouped by the ingredient's stored base unit. */
    public List<Map<String, Object>> loadActivitySummaries(Connection connection,
            ActivityFilters filters) throws SQLException {
        StringBuilder sql = new StringBuilder(ACTIVITY_QUERY);
        List<Date> parameters = new ArrayList<>();
        if (filters.fromDate() != null) {
            sql.append(" AND po.ordered_at >= ?");
            parameters.add(Date.valueOf(filters.fromDate()));
        }
        if (filters.toDate() != null) {
            sql.append(" AND po.ordered_at < ? + 1");
            parameters.add(Date.valueOf(filters.toDate()));
        }
        sql.append(" GROUP BY TRUNC(po.ordered_at, 'MM'), i.base_unit")
                .append(" ORDER BY activity_month, i.base_unit");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setDate(index + 1, parameters.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Date month = resultSet.getDate("activity_month");
                    row.put("activityMonth", month == null ? null : month.toLocalDate().toString());
                    row.put("baseUnit", resultSet.getString("base_unit"));
                    row.put("orderedQty", resultSet.getBigDecimal("ordered_qty"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * Validates current reference rows, inserts the order and all lines, and commits once.
     * Any SQL or reference-validation failure rolls the whole operation back.
     */
    public long createPurchaseOrder(PurchaseOrderDraft draft)
            throws SQLException, InvalidReferenceException, DuplicatePurchaseOrderException {
        try (Connection connection = OracleConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireActive(connection, ACTIVE_VENDOR_QUERY, draft.vendorId(), "vendor");
                requireActive(connection, ACTIVE_BLOCK_QUERY, draft.blockId(), "mess block");
                requireUniqueOrderNumber(connection, draft.poNumber());

                Set<Long> ingredientIds = new HashSet<>();
                for (PurchaseOrderItemDraft item : draft.items()) {
                    requireActive(connection, ACTIVE_INGREDIENT_QUERY, item.ingredientId(), "ingredient");
                    if (!ingredientIds.add(item.ingredientId())) {
                        throw new InvalidReferenceException(
                                "Select each ingredient only once. Combine its quantity into one line.");
                    }
                }

                long poId = insertPurchaseOrder(connection, draft);
                insertItems(connection, poId, draft.items());
                connection.commit();
                return poId;
            } catch (SQLException | InvalidReferenceException | DuplicatePurchaseOrderException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private List<Map<String, Object>> loadVendors(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(VENDORS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("vendorId", resultSet.getLong("vendor_id"));
                row.put("vendorCode", resultSet.getString("vendor_code"));
                row.put("vendorName", resultSet.getString("vendor_name"));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadBlocks(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(BLOCKS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("blockId", resultSet.getLong("block_id"));
                row.put("blockCode", resultSet.getString("block_code"));
                row.put("blockName", resultSet.getString("block_name"));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadIngredients(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(INGREDIENTS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ingredientId", resultSet.getLong("ingredient_id"));
                row.put("ingredientCode", resultSet.getString("ingredient_code"));
                row.put("ingredientName", resultSet.getString("ingredient_name"));
                row.put("baseUnit", resultSet.getString("base_unit"));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadPurchaseOrders(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PURCHASE_ORDERS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("poNumber", resultSet.getString("po_number"));
                row.put("vendorName", resultSet.getString("vendor_name"));
                row.put("blockName", resultSet.getString("block_name"));
                row.put("orderedAt", resultSet.getDate("ordered_at"));
                row.put("expectedBy", resultSet.getDate("expected_by"));
                row.put("status", resultSet.getString("status"));
                row.put("approvedAmount", resultSet.getBigDecimal("approved_amount"));
                row.put("itemCount", resultSet.getLong("item_count"));
                row.put("orderValue", resultSet.getBigDecimal("order_value"));
                rows.add(row);
            }
        }
        return rows;
    }

    private void requireActive(Connection connection, String query, long id, String label)
            throws SQLException, InvalidReferenceException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new InvalidReferenceException(
                            "Choose an active existing " + label + " from the list.");
                }
            }
        }
    }

    private void requireUniqueOrderNumber(Connection connection, String poNumber)
            throws SQLException, DuplicatePurchaseOrderException {
        try (PreparedStatement statement = connection.prepareStatement(DUPLICATE_PO_QUERY)) {
            statement.setString(1, poNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    throw new DuplicatePurchaseOrderException(
                            "That purchase-order number already exists. Enter a unique number.");
                }
            }
        }
    }

    private long insertPurchaseOrder(Connection connection, PurchaseOrderDraft draft) throws SQLException {
        String insertSql = """
                INSERT INTO purchase_orders
                    (po_number, vendor_id, block_id, ordered_at, expected_by, approved_amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, draft.poNumber());
            statement.setLong(2, draft.vendorId());
            statement.setLong(3, draft.blockId());
            statement.setDate(4, Date.valueOf(draft.orderedAt()));
            if (draft.expectedBy() == null) {
                statement.setNull(5, Types.DATE);
            } else {
                statement.setDate(5, Date.valueOf(draft.expectedBy()));
            }
            statement.setBigDecimal(6, draft.approvedAmount());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Purchase-order insert did not create one row.");
            }
        }

        String idSql = "SELECT po_id FROM purchase_orders WHERE po_number = ?";
        try (PreparedStatement statement = connection.prepareStatement(idSql)) {
            statement.setString(1, draft.poNumber());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Inserted purchase order could not be retrieved.");
                }
                return resultSet.getLong("po_id");
            }
        }
    }

    private void insertItems(Connection connection, long poId, List<PurchaseOrderItemDraft> items)
            throws SQLException {
        String sql = """
                INSERT INTO purchase_order_items (po_id, ingredient_id, ordered_qty, unit_price)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (PurchaseOrderItemDraft item : items) {
                statement.setLong(1, poId);
                statement.setLong(2, item.ingredientId());
                statement.setBigDecimal(3, item.orderedQty());
                statement.setBigDecimal(4, item.unitPrice());
                statement.addBatch();
            }
            int[] results = statement.executeBatch();
            if (results.length != items.size()) {
                throw new SQLException("Purchase-order item batch was incomplete.");
            }
            for (int result : results) {
                if (result == Statement.EXECUTE_FAILED) {
                    throw new SQLException("A purchase-order item insert failed.");
                }
            }
        }
    }

    private void rollback(Connection connection, Exception originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    public record PageData(List<Map<String, Object>> vendors,
            List<Map<String, Object>> blocks,
            List<Map<String, Object>> ingredients,
            List<Map<String, Object>> purchaseOrders) {
    }

    public record ActivityFilters(LocalDate fromDate, LocalDate toDate) {
    }

    public record PurchaseOrderDraft(String poNumber, long vendorId, long blockId,
            LocalDate orderedAt, LocalDate expectedBy, BigDecimal approvedAmount,
            List<PurchaseOrderItemDraft> items) {
    }

    public record PurchaseOrderItemDraft(long ingredientId, BigDecimal orderedQty,
            BigDecimal unitPrice) {
    }

    public static final class InvalidReferenceException extends Exception {
        private static final long serialVersionUID = 1L;

        private InvalidReferenceException(String message) {
            super(message);
        }
    }

    public static final class DuplicatePurchaseOrderException extends Exception {
        private static final long serialVersionUID = 1L;

        private DuplicatePurchaseOrderException(String message) {
            super(message);
        }
    }
}

