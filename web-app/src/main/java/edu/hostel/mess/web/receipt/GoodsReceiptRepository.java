package edu.hostel.mess.web.receipt;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.hostel.mess.web.db.OracleConnectionFactory;

/** Queries receipt-eligible orders and records verified receipts atomically. */
public final class GoodsReceiptRepository {
    private static final String ELIGIBLE_ORDERS_QUERY = """
            SELECT po.po_id,
                   po.po_number,
                   v.vendor_name,
                   b.block_name,
                   po.ordered_at,
                   po.expected_by,
                   po.status,
                   (SELECT COUNT(*)
                    FROM purchase_order_items open_item
                    WHERE open_item.po_id = po.po_id
                      AND open_item.ordered_qty > NVL((
                          SELECT SUM(prior_item.received_qty)
                          FROM delivery_receipt_items prior_item
                          WHERE prior_item.po_id = open_item.po_id
                            AND prior_item.po_item_id = open_item.po_item_id
                      ), 0)) AS outstanding_item_count
            FROM purchase_orders po
            JOIN vendors v ON v.vendor_id = po.vendor_id
            JOIN mess_blocks b ON b.block_id = po.block_id
            WHERE po.status IN ('OPEN', 'PARTIAL')
              AND EXISTS (
                  SELECT 1
                  FROM purchase_order_items open_item
                  WHERE open_item.po_id = po.po_id
                    AND open_item.ordered_qty > NVL((
                        SELECT SUM(prior_item.received_qty)
                        FROM delivery_receipt_items prior_item
                        WHERE prior_item.po_id = open_item.po_id
                          AND prior_item.po_item_id = open_item.po_item_id
                    ), 0)
              )
            ORDER BY po.ordered_at DESC, po.po_id DESC
            """;

    private static final String SELECT_ORDER_QUERY = """
            SELECT po.po_id, po.po_number, po.vendor_id, po.block_id,
                   v.vendor_name, b.block_name, po.ordered_at, po.expected_by, po.status
            FROM purchase_orders po
            JOIN vendors v ON v.vendor_id = po.vendor_id
            JOIN mess_blocks b ON b.block_id = po.block_id
            WHERE po.po_id = ?
              AND po.status IN ('OPEN', 'PARTIAL')
              AND EXISTS (
                  SELECT 1
                  FROM purchase_order_items open_item
                  WHERE open_item.po_id = po.po_id
                    AND open_item.ordered_qty > NVL((
                        SELECT SUM(prior_item.received_qty)
                        FROM delivery_receipt_items prior_item
                        WHERE prior_item.po_id = open_item.po_id
                          AND prior_item.po_item_id = open_item.po_item_id
                    ), 0)
              )
            """;

    private static final String OUTSTANDING_ITEMS_QUERY = """
            SELECT poi.po_item_id,
                   i.ingredient_name,
                   i.base_unit,
                   poi.ordered_qty,
                   NVL((SELECT SUM(dri.received_qty)
                        FROM delivery_receipt_items dri
                        WHERE dri.po_id = poi.po_id
                          AND dri.po_item_id = poi.po_item_id), 0) AS received_to_date,
                   GREATEST(poi.ordered_qty - NVL((
                       SELECT SUM(dri.received_qty)
                       FROM delivery_receipt_items dri
                       WHERE dri.po_id = poi.po_id
                         AND dri.po_item_id = poi.po_item_id
                   ), 0), 0) AS remaining_qty
            FROM purchase_order_items poi
            JOIN ingredients i ON i.ingredient_id = poi.ingredient_id
            WHERE poi.po_id = ?
              AND poi.ordered_qty > NVL((
                  SELECT SUM(dri.received_qty)
                  FROM delivery_receipt_items dri
                  WHERE dri.po_id = poi.po_id
                    AND dri.po_item_id = poi.po_item_id
              ), 0)
            ORDER BY i.ingredient_name, poi.po_item_id
            """;

    private static final String LOCK_ORDER_QUERY =
            "SELECT block_id, status FROM purchase_orders WHERE po_id = ? FOR UPDATE";
    private static final String DUPLICATE_INVOICE_QUERY =
            "SELECT 1 FROM delivery_receipts WHERE po_id = ? AND invoice_reference = ?";
    private static final String ORDER_ITEMS_QUERY = """
            SELECT poi.po_item_id,
                   poi.ingredient_id,
                   poi.ordered_qty,
                   NVL((SELECT SUM(dri.received_qty)
                        FROM delivery_receipt_items dri
                        WHERE dri.po_id = poi.po_id
                          AND dri.po_item_id = poi.po_item_id), 0) AS received_to_date
            FROM purchase_order_items poi
            WHERE poi.po_id = ?
            ORDER BY poi.po_item_id
            """;
    private static final String INSERT_RECEIPT_QUERY = """
            INSERT INTO delivery_receipts
                (po_id, invoice_reference, invoice_amount, arrived_at, verified_by,
                 verified_at, inventory_processed_at, notes)
            VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP, SYSTIMESTAMP, ?)
            """;
    private static final String SELECT_RECEIPT_ID_QUERY = """
            SELECT receipt_id
            FROM delivery_receipts
            WHERE po_id = ? AND invoice_reference = ?
            """;
    private static final String INSERT_RECEIPT_ITEM_QUERY = """
            INSERT INTO delivery_receipt_items
                (receipt_id, po_id, po_item_id, received_qty, accepted_qty, rejected_qty, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_RECEIPT_ITEM_ID_QUERY = """
            SELECT receipt_item_id
            FROM delivery_receipt_items
            WHERE receipt_id = ? AND po_item_id = ?
            """;
    private static final String UPDATE_BALANCE_QUERY = """
            UPDATE inventory_balances
            SET on_hand_qty = on_hand_qty + ?, updated_at = SYSTIMESTAMP
            WHERE block_id = ? AND ingredient_id = ?
            """;
    private static final String INSERT_BALANCE_QUERY = """
            INSERT INTO inventory_balances
                (block_id, ingredient_id, on_hand_qty, reorder_level, updated_at)
            VALUES (?, ?, ?, 0, SYSTIMESTAMP)
            """;
    private static final String SELECT_BALANCE_QUERY = """
            SELECT on_hand_qty
            FROM inventory_balances
            WHERE block_id = ? AND ingredient_id = ?
            FOR UPDATE
            """;
    private static final String INSERT_INVENTORY_TX_QUERY = """
            INSERT INTO inventory_transactions
                (transaction_reference, block_id, ingredient_id, transaction_type,
                 quantity_delta, balance_after_qty, source_receipt_item_id, notes)
            VALUES (?, ?, ?, 'RECEIPT', ?, ?, ?, ?)
            """;
    private static final String COUNT_OUTSTANDING_QUERY = """
            SELECT COUNT(*)
            FROM purchase_order_items poi
            WHERE poi.po_id = ?
              AND poi.ordered_qty > NVL((
                  SELECT SUM(dri.received_qty)
                  FROM delivery_receipt_items dri
                  WHERE dri.po_id = poi.po_id
                    AND dri.po_item_id = poi.po_item_id
              ), 0)
            """;
    private static final String UPDATE_ORDER_STATUS_QUERY =
            "UPDATE purchase_orders SET status = ? WHERE po_id = ?";

    public PageData loadPage(Connection connection, Long selectedPoId)
            throws SQLException, IneligiblePurchaseOrderException {
        List<Map<String, Object>> eligibleOrders = loadEligibleOrders(connection);
        Map<String, Object> selectedOrder = null;
        List<Map<String, Object>> outstandingItems = List.of();

        if (selectedPoId != null) {
            selectedOrder = loadSelectedOrder(connection, selectedPoId);
            if (selectedOrder == null) {
                throw new IneligiblePurchaseOrderException(
                        "That purchase order is no longer eligible for a receipt. Choose an open order from the list.");
            }
            outstandingItems = loadOutstandingItems(connection, selectedPoId);
        }

        return new PageData(eligibleOrders, selectedOrder, outstandingItems);
    }

    public PageData loadPage(Connection connection) throws SQLException {
        return new PageData(loadEligibleOrders(connection), null, List.of());
    }

    /**
     * Serializes receipt processing per purchase order. The receipt, item lines,
     * accepted-stock updates, stock ledger rows, and order status commit together.
     */
    public long createReceipt(ReceiptDraft draft)
            throws SQLException, IneligiblePurchaseOrderException, InvalidReceiptException {
        try (Connection connection = OracleConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                long blockId = lockEligibleOrder(connection, draft.poId());
                rejectDuplicateInvoice(connection, draft.poId(), draft.invoiceReference());
                Map<Long, OrderItem> orderItems = loadOrderItems(connection, draft.poId());
                validateReceiptLines(draft, orderItems);

                long receiptId = insertReceipt(connection, draft);
                for (ReceiptLineDraft line : draft.lines()) {
                    if (line.receivedQty().signum() == 0) {
                        continue;
                    }
                    long receiptItemId = insertReceiptItem(connection, receiptId, draft, line);
                    if (line.acceptedQty().signum() > 0) {
                        OrderItem orderItem = orderItems.get(line.poItemId());
                        postAcceptedInventory(connection, receiptId, receiptItemId, blockId,
                                orderItem.ingredientId(), line.acceptedQty(), draft.invoiceReference());
                    }
                }

                updatePurchaseOrderStatus(connection, draft.poId());
                connection.commit();
                return receiptId;
            } catch (SQLException | IneligiblePurchaseOrderException | InvalidReceiptException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private List<Map<String, Object>> loadEligibleOrders(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(ELIGIBLE_ORDERS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("poId", resultSet.getLong("po_id"));
                row.put("poNumber", resultSet.getString("po_number"));
                row.put("vendorName", resultSet.getString("vendor_name"));
                row.put("blockName", resultSet.getString("block_name"));
                row.put("orderedAt", resultSet.getDate("ordered_at"));
                row.put("expectedBy", resultSet.getDate("expected_by"));
                row.put("status", resultSet.getString("status"));
                row.put("outstandingItemCount", resultSet.getLong("outstanding_item_count"));
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<String, Object> loadSelectedOrder(Connection connection, long poId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ORDER_QUERY)) {
            statement.setLong(1, poId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("poId", resultSet.getLong("po_id"));
                row.put("poNumber", resultSet.getString("po_number"));
                row.put("vendorName", resultSet.getString("vendor_name"));
                row.put("blockName", resultSet.getString("block_name"));
                row.put("orderedAt", resultSet.getDate("ordered_at"));
                row.put("expectedBy", resultSet.getDate("expected_by"));
                row.put("status", resultSet.getString("status"));
                return row;
            }
        }
    }

    private List<Map<String, Object>> loadOutstandingItems(Connection connection, long poId)
            throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(OUTSTANDING_ITEMS_QUERY)) {
            statement.setLong(1, poId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("poItemId", resultSet.getLong("po_item_id"));
                    row.put("ingredientName", resultSet.getString("ingredient_name"));
                    row.put("baseUnit", resultSet.getString("base_unit"));
                    row.put("orderedQty", resultSet.getBigDecimal("ordered_qty"));
                    row.put("receivedToDate", resultSet.getBigDecimal("received_to_date"));
                    row.put("remainingQty", resultSet.getBigDecimal("remaining_qty"));
                    row.put("receivedQty", "0");
                    row.put("acceptedQty", "0");
                    row.put("rejectedQty", "0");
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private long lockEligibleOrder(Connection connection, long poId)
            throws SQLException, IneligiblePurchaseOrderException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_ORDER_QUERY)) {
            statement.setLong(1, poId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IneligiblePurchaseOrderException(
                            "That purchase order is not available for receipt. Refresh the page and choose an open order.");
                }
                String status = resultSet.getString("status");
                if (!"OPEN".equals(status) && !"PARTIAL".equals(status)) {
                    throw new IneligiblePurchaseOrderException(
                            "Only open or partially received purchase orders can receive another delivery.");
                }
                return resultSet.getLong("block_id");
            }
        }
    }

    private void rejectDuplicateInvoice(Connection connection, long poId, String invoiceReference)
            throws SQLException, InvalidReceiptException {
        try (PreparedStatement statement = connection.prepareStatement(DUPLICATE_INVOICE_QUERY)) {
            statement.setLong(1, poId);
            statement.setString(2, invoiceReference);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    throw new InvalidReceiptException(
                            "That invoice reference is already recorded for this purchase order.");
                }
            }
        }
    }

    private Map<Long, OrderItem> loadOrderItems(Connection connection, long poId) throws SQLException {
        Map<Long, OrderItem> items = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(ORDER_ITEMS_QUERY)) {
            statement.setLong(1, poId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long itemId = resultSet.getLong("po_item_id");
                    items.put(itemId, new OrderItem(itemId, resultSet.getLong("ingredient_id"),
                            resultSet.getBigDecimal("ordered_qty"),
                            resultSet.getBigDecimal("received_to_date")));
                }
            }
        }
        return items;
    }

    private void validateReceiptLines(ReceiptDraft draft, Map<Long, OrderItem> orderItems)
            throws IneligiblePurchaseOrderException, InvalidReceiptException {
        if (orderItems.isEmpty()) {
            throw new IneligiblePurchaseOrderException(
                    "This purchase order has no remaining item quantities to receive.");
        }
        if (draft.lines().isEmpty()) {
            throw new InvalidReceiptException("Enter at least one delivery item.");
        }

        boolean hasReceivedQuantity = false;
        Map<Long, Boolean> seenItems = new LinkedHashMap<>();
        for (ReceiptLineDraft line : draft.lines()) {
            OrderItem orderItem = orderItems.get(line.poItemId());
            if (orderItem == null || seenItems.put(line.poItemId(), Boolean.TRUE) != null) {
                throw new InvalidReceiptException(
                        "One or more receipt items do not belong to this purchase order. Refresh and try again.");
            }
            if (line.receivedQty().signum() < 0 || line.acceptedQty().signum() < 0
                    || line.rejectedQty().signum() < 0) {
                throw new InvalidReceiptException("Receipt quantities cannot be negative.");
            }
            if (line.acceptedQty().add(line.rejectedQty()).compareTo(line.receivedQty()) != 0) {
                throw new InvalidReceiptException(
                        "Accepted and rejected quantities must add up to the received quantity for each item.");
            }
            BigDecimal remaining = orderItem.orderedQty().subtract(orderItem.receivedToDate());
            if (line.receivedQty().compareTo(remaining) > 0) {
                throw new InvalidReceiptException(
                        "A received quantity exceeds the remaining ordered quantity. Refresh the page and review the latest balance.");
            }
            hasReceivedQuantity |= line.receivedQty().signum() > 0;
        }
        if (!hasReceivedQuantity) {
            throw new InvalidReceiptException("Enter a positive received quantity for at least one item.");
        }
    }

    private long insertReceipt(Connection connection, ReceiptDraft draft) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RECEIPT_QUERY)) {
            statement.setLong(1, draft.poId());
            statement.setString(2, draft.invoiceReference());
            statement.setBigDecimal(3, draft.invoiceAmount());
            statement.setTimestamp(4, Timestamp.valueOf(draft.arrivedAt()));
            statement.setString(5, draft.verifiedBy());
            if (draft.notes() == null || draft.notes().isBlank()) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, draft.notes());
            }
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Receipt insert did not create one row.");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(SELECT_RECEIPT_ID_QUERY)) {
            statement.setLong(1, draft.poId());
            statement.setString(2, draft.invoiceReference());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Inserted receipt ID could not be retrieved.");
                }
                return resultSet.getLong("receipt_id");
            }
        }
    }

    private long insertReceiptItem(Connection connection, long receiptId, ReceiptDraft draft,
            ReceiptLineDraft line) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RECEIPT_ITEM_QUERY)) {
            statement.setLong(1, receiptId);
            statement.setLong(2, draft.poId());
            statement.setLong(3, line.poItemId());
            statement.setBigDecimal(4, line.receivedQty());
            statement.setBigDecimal(5, line.acceptedQty());
            statement.setBigDecimal(6, line.rejectedQty());
            if (line.notes() == null || line.notes().isBlank()) {
                statement.setNull(7, Types.VARCHAR);
            } else {
                statement.setString(7, line.notes());
            }
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Receipt item insert did not create one row.");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(SELECT_RECEIPT_ITEM_ID_QUERY)) {
            statement.setLong(1, receiptId);
            statement.setLong(2, line.poItemId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Inserted receipt item ID could not be retrieved.");
                }
                return resultSet.getLong("receipt_item_id");
            }
        }
    }

    private void postAcceptedInventory(Connection connection, long receiptId, long receiptItemId,
            long blockId, long ingredientId, BigDecimal acceptedQty, String invoiceReference)
            throws SQLException {
        addToInventoryBalance(connection, blockId, ingredientId, acceptedQty);
        BigDecimal balanceAfter = lockAndReadBalance(connection, blockId, ingredientId);
        String transactionReference = "GR-" + receiptId + "-" + receiptItemId;
        String notes = "Accepted quantity from invoice " + invoiceReference;
        try (PreparedStatement statement = connection.prepareStatement(INSERT_INVENTORY_TX_QUERY)) {
            statement.setString(1, transactionReference);
            statement.setLong(2, blockId);
            statement.setLong(3, ingredientId);
            statement.setBigDecimal(4, acceptedQty);
            statement.setBigDecimal(5, balanceAfter);
            statement.setLong(6, receiptItemId);
            statement.setString(7, notes);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Inventory transaction did not create one row.");
            }
        }
    }

    private void addToInventoryBalance(Connection connection, long blockId, long ingredientId,
            BigDecimal acceptedQty) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(UPDATE_BALANCE_QUERY)) {
            update.setBigDecimal(1, acceptedQty);
            update.setLong(2, blockId);
            update.setLong(3, ingredientId);
            if (update.executeUpdate() == 1) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(INSERT_BALANCE_QUERY)) {
            insert.setLong(1, blockId);
            insert.setLong(2, ingredientId);
            insert.setBigDecimal(3, acceptedQty);
            insert.executeUpdate();
        } catch (SQLException insertFailure) {
            if (insertFailure.getErrorCode() != 1) {
                throw insertFailure;
            }
            // A concurrent receipt created the composite-key balance row after the UPDATE above.
            try (PreparedStatement update = connection.prepareStatement(UPDATE_BALANCE_QUERY)) {
                update.setBigDecimal(1, acceptedQty);
                update.setLong(2, blockId);
                update.setLong(3, ingredientId);
                if (update.executeUpdate() != 1) {
                    throw insertFailure;
                }
            }
        }
    }

    private BigDecimal lockAndReadBalance(Connection connection, long blockId, long ingredientId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BALANCE_QUERY)) {
            statement.setLong(1, blockId);
            statement.setLong(2, ingredientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Updated inventory balance could not be retrieved.");
                }
                return resultSet.getBigDecimal("on_hand_qty");
            }
        }
    }

    private void updatePurchaseOrderStatus(Connection connection, long poId) throws SQLException {
        long outstandingCount;
        try (PreparedStatement statement = connection.prepareStatement(COUNT_OUTSTANDING_QUERY)) {
            statement.setLong(1, poId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Purchase-order completion status could not be calculated.");
                }
                outstandingCount = resultSet.getLong(1);
            }
        }
        String nextStatus = outstandingCount == 0 ? "CLOSED" : "PARTIAL";
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_ORDER_STATUS_QUERY)) {
            statement.setString(1, nextStatus);
            statement.setLong(2, poId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Purchase-order status could not be updated.");
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

    public record PageData(List<Map<String, Object>> eligibleOrders,
            Map<String, Object> selectedOrder,
            List<Map<String, Object>> outstandingItems) {
    }

    public record ReceiptDraft(long poId, String invoiceReference, BigDecimal invoiceAmount,
            LocalDateTime arrivedAt, String verifiedBy, String notes, List<ReceiptLineDraft> lines) {
        public ReceiptDraft {
            lines = List.copyOf(lines);
        }
    }

    public record ReceiptLineDraft(long poItemId, BigDecimal receivedQty,
            BigDecimal acceptedQty, BigDecimal rejectedQty, String notes) {
    }

    private record OrderItem(long poItemId, long ingredientId,
            BigDecimal orderedQty, BigDecimal receivedToDate) {
    }

    public static final class IneligiblePurchaseOrderException extends Exception {
        private static final long serialVersionUID = 1L;

        public IneligiblePurchaseOrderException(String message) {
            super(message);
        }
    }

    public static final class InvalidReceiptException extends Exception {
        private static final long serialVersionUID = 1L;

        public InvalidReceiptException(String message) {
            super(message);
        }
    }
}
