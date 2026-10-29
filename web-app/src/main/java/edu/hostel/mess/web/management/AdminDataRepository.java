package edu.hostel.mess.web.management;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Purpose-specific admin reads and auditable operations. Inventory ledger rows are never edited or deleted. */
public final class AdminDataRepository {
    private static final int PAGE_SIZE = 40;

    public Overview loadOverview(Connection connection) throws SQLException {
        String sql = """
                SELECT (SELECT COUNT(*) FROM vendors) vendors,
                       (SELECT COUNT(*) FROM ingredients) ingredients,
                       (SELECT COUNT(*) FROM mess_blocks) blocks,
                       (SELECT COUNT(*) FROM purchase_orders) orders_count,
                       (SELECT COUNT(*) FROM delivery_receipts) receipts,
                       (SELECT COUNT(*) FROM inventory_balances) balances,
                       (SELECT COUNT(*) FROM inventory_transactions) transactions_count,
                       (SELECT COUNT(*) FROM daily_mess_records) mess_records,
                       (SELECT COUNT(*) FROM app_users) users_count,
                       (SELECT COUNT(*) FROM vendor_contract_rules) contract_rules,
                       (SELECT COUNT(*) FROM vendor_penalty_reviews) penalty_reviews,
                       (SELECT COUNT(*) FROM audit_log) audit_count
                FROM dual
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) throw new SQLException("Admin overview query returned no row.");
            return new Overview(rs.getLong("vendors"), rs.getLong("ingredients"), rs.getLong("blocks"),
                    rs.getLong("orders_count"), rs.getLong("receipts"), rs.getLong("balances"),
                    rs.getLong("transactions_count"), rs.getLong("mess_records"), rs.getLong("users_count"),
                    rs.getLong("contract_rules"), rs.getLong("penalty_reviews"), rs.getLong("audit_count"));
        }
    }

    public List<Map<String, Object>> loadAuditEvents(Connection connection) throws SQLException {
        return queryRows(connection, """
                SELECT audit_id, table_name, record_key, operation_type, changed_at, changed_by
                FROM audit_log ORDER BY changed_at DESC, audit_id DESC FETCH FIRST 100 ROWS ONLY
                """, "audit_id", "table_name", "record_key", "operation_type", "changed_at", "changed_by");
    }

    public List<Map<String, Object>> loadOrders(Connection connection, String search) throws SQLException {
        String sql = """
                SELECT po.po_id, po.po_number, po.ordered_at, po.expected_by, po.status,
                       po.approved_amount, v.vendor_name, b.block_name,
                       (SELECT COUNT(*) FROM purchase_order_items poi WHERE poi.po_id = po.po_id) item_count,
                       (SELECT COUNT(*) FROM delivery_receipts dr WHERE dr.po_id = po.po_id) receipt_count,
                       (SELECT COUNT(*) FROM vendor_penalty_reviews pr WHERE pr.po_id = po.po_id) review_count,
                       (SELECT COUNT(*) FROM audit_log al WHERE al.table_name = 'PURCHASE_ORDERS'
                          AND al.record_key = TO_CHAR(po.po_id)) audit_count
                FROM purchase_orders po
                JOIN vendors v ON v.vendor_id = po.vendor_id
                JOIN mess_blocks b ON b.block_id = po.block_id
                WHERE (? IS NULL OR INSTR(LOWER(po.po_number), LOWER(?)) > 0
                       OR INSTR(LOWER(v.vendor_name), LOWER(?)) > 0)
                ORDER BY po.ordered_at DESC, po.po_id DESC
                FETCH FIRST 40 ROWS ONLY
                """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String query = blankToNull(search);
            statement.setString(1, query);
            statement.setString(2, query);
            statement.setString(3, query);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(row(rs, "po_id", "po_number", "ordered_at", "expected_by", "status",
                        "approved_amount", "vendor_name", "block_name", "item_count", "receipt_count", "review_count", "audit_count"));
            }
        }
        return List.copyOf(result);
    }

    public List<Map<String, Object>> loadOrderItems(Connection connection, long poId) throws SQLException {
        String sql = """
                SELECT poi.po_item_id, i.ingredient_name, i.base_unit, poi.ordered_qty, poi.unit_price,
                       NVL((SELECT SUM(dri.received_qty) FROM delivery_receipt_items dri
                            WHERE dri.po_id = poi.po_id AND dri.po_item_id = poi.po_item_id), 0) received_qty,
                       NVL((SELECT SUM(dri.accepted_qty) FROM delivery_receipt_items dri
                            WHERE dri.po_id = poi.po_id AND dri.po_item_id = poi.po_item_id), 0) accepted_qty,
                       NVL((SELECT SUM(dri.rejected_qty) FROM delivery_receipt_items dri
                            WHERE dri.po_id = poi.po_id AND dri.po_item_id = poi.po_item_id), 0) rejected_qty
                FROM purchase_order_items poi JOIN ingredients i ON i.ingredient_id = poi.ingredient_id
                WHERE poi.po_id = ? ORDER BY poi.po_item_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, poId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) rows.add(row(rs, "po_item_id", "ingredient_name", "base_unit", "ordered_qty",
                        "unit_price", "received_qty", "accepted_qty", "rejected_qty"));
                return List.copyOf(rows);
            }
        }
    }

    public List<Map<String, Object>> loadCleanupOrders(Connection connection) throws SQLException {
        String sql = """
                SELECT po.po_id, po.po_number, po.ordered_at, po.expected_by, po.status,
                       v.vendor_name, b.block_name,
                       (SELECT COUNT(*) FROM purchase_order_items poi WHERE poi.po_id = po.po_id) item_count
                FROM purchase_orders po
                JOIN vendors v ON v.vendor_id = po.vendor_id
                JOIN mess_blocks b ON b.block_id = po.block_id
                WHERE NOT EXISTS (SELECT 1 FROM delivery_receipts dr WHERE dr.po_id = po.po_id)
                  AND NOT EXISTS (SELECT 1 FROM vendor_penalty_reviews pr WHERE pr.po_id = po.po_id)
                ORDER BY po.ordered_at DESC, po.po_id DESC
                FETCH FIRST 100 ROWS ONLY
                """;
        return queryRows(connection, sql, "po_id", "po_number", "ordered_at", "expected_by", "status",
                "vendor_name", "block_name", "item_count");
    }

    public List<Map<String, Object>> loadReceipts(Connection connection, String search) throws SQLException {
        String sql = """
                SELECT dr.receipt_id, dr.po_id, po.po_number, v.vendor_name, dr.invoice_reference,
                       dr.invoice_amount, dr.arrived_at, dr.verified_at, dr.inventory_processed_at,
                       NVL((SELECT SUM(dri.received_qty) FROM delivery_receipt_items dri
                            WHERE dri.receipt_id = dr.receipt_id), 0) received_qty,
                       NVL((SELECT SUM(dri.accepted_qty) FROM delivery_receipt_items dri
                            WHERE dri.receipt_id = dr.receipt_id), 0) accepted_qty,
                       NVL((SELECT SUM(dri.rejected_qty) FROM delivery_receipt_items dri
                            WHERE dri.receipt_id = dr.receipt_id), 0) rejected_qty,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM audit_log al
                           WHERE al.table_name = 'DELIVERY_RECEIPTS'
                             AND al.record_key = TO_CHAR(dr.receipt_id)
                             AND DBMS_LOB.INSTR(al.new_values, 'INVENTORY_REVERSAL') > 0
                       ) THEN 'Y' ELSE 'N' END reversed_flag
                FROM delivery_receipts dr
                JOIN purchase_orders po ON po.po_id = dr.po_id
                JOIN vendors v ON v.vendor_id = po.vendor_id
                WHERE (? IS NULL OR INSTR(LOWER(po.po_number), LOWER(?)) > 0
                       OR INSTR(LOWER(dr.invoice_reference), LOWER(?)) > 0
                       OR INSTR(LOWER(v.vendor_name), LOWER(?)) > 0)
                ORDER BY dr.arrived_at DESC, dr.receipt_id DESC
                FETCH FIRST 40 ROWS ONLY
                """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String query = blankToNull(search);
            for (int i = 1; i <= 4; i++) statement.setString(i, query);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(row(rs, "receipt_id", "po_id", "po_number", "vendor_name",
                        "invoice_reference", "invoice_amount", "arrived_at", "verified_at", "inventory_processed_at",
                        "received_qty", "accepted_qty", "rejected_qty", "reversed_flag"));
            }
        }
        return List.copyOf(result);
    }

    public Map<String, Object> loadReceipt(Connection connection, long receiptId) throws SQLException {
        String sql = """
                SELECT dr.receipt_id, dr.po_id, po.po_number, v.vendor_name, b.block_name,
                       dr.invoice_reference, dr.invoice_amount, dr.arrived_at, dr.verified_by,
                       dr.verified_at, dr.inventory_processed_at,
                       CASE WHEN EXISTS (SELECT 1 FROM audit_log al
                           WHERE al.table_name='DELIVERY_RECEIPTS' AND al.record_key=TO_CHAR(dr.receipt_id)
                             AND DBMS_LOB.INSTR(al.new_values, 'INVENTORY_REVERSAL') > 0)
                            THEN 'Y' ELSE 'N' END reversed_flag
                FROM delivery_receipts dr
                JOIN purchase_orders po ON po.po_id = dr.po_id
                JOIN vendors v ON v.vendor_id = po.vendor_id
                JOIN mess_blocks b ON b.block_id = po.block_id
                WHERE dr.receipt_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? row(rs, "receipt_id", "po_id", "po_number", "vendor_name", "block_name",
                        "invoice_reference", "invoice_amount", "arrived_at", "verified_by", "verified_at",
                        "inventory_processed_at", "reversed_flag") : null;
            }
        }
    }

    public List<Map<String, Object>> loadReceiptItems(Connection connection, long receiptId) throws SQLException {
        String sql = """
                SELECT dri.receipt_item_id, i.ingredient_name, i.base_unit, dri.received_qty,
                       dri.accepted_qty, dri.rejected_qty, dri.notes
                FROM delivery_receipt_items dri
                JOIN purchase_order_items poi ON poi.po_id=dri.po_id AND poi.po_item_id=dri.po_item_id
                JOIN ingredients i ON i.ingredient_id=poi.ingredient_id
                WHERE dri.receipt_id = ? ORDER BY dri.receipt_item_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) rows.add(row(rs, "receipt_item_id", "ingredient_name", "base_unit",
                        "received_qty", "accepted_qty", "rejected_qty", "notes"));
                return List.copyOf(rows);
            }
        }
    }

    public List<Map<String, Object>> loadContractRules(Connection connection) throws SQLException {
        return queryRows(connection, """
                SELECT r.rule_id, v.vendor_name, r.rule_code, r.rule_name, r.discrepancy_type,
                       r.effective_from, r.effective_to, r.tolerance_percent, r.penalty_percent,
                       r.max_penalty, r.terms_text
                FROM vendor_contract_rules r JOIN vendors v ON v.vendor_id=r.vendor_id
                ORDER BY v.vendor_name, r.effective_from DESC, r.rule_id DESC
                FETCH FIRST 100 ROWS ONLY
                """, "rule_id", "vendor_name", "rule_code", "rule_name", "discrepancy_type",
                "effective_from", "effective_to", "tolerance_percent", "penalty_percent", "max_penalty", "terms_text");
    }

    public List<Map<String, Object>> loadPenaltyReviews(Connection connection) throws SQLException {
        return queryRows(connection, """
                SELECT pr.review_id, po.po_number, v.vendor_name, r.rule_code, pr.eligible_line_count,
                       pr.eligible_value, pr.proposed_amount, pr.approved_amount, pr.status,
                       pr.reviewed_by, pr.reviewed_at, pr.calculation_basis
                FROM vendor_penalty_reviews pr
                JOIN purchase_orders po ON po.po_id=pr.po_id
                JOIN vendors v ON v.vendor_id=po.vendor_id
                JOIN vendor_contract_rules r ON r.rule_id=pr.rule_id
                ORDER BY pr.created_at DESC, pr.review_id DESC
                FETCH FIRST 100 ROWS ONLY
                """, "review_id", "po_number", "vendor_name", "rule_code", "eligible_line_count",
                "eligible_value", "proposed_amount", "approved_amount", "status", "reviewed_by",
                "reviewed_at", "calculation_basis");
    }

    public List<Map<String, Object>> loadDailyRecords(Connection connection, String search) throws SQLException {
        String sql = """
                SELECT mr.record_id, mr.block_id, b.block_name, mr.record_date, mr.meals_served,
                       mr.prepared_kg, mr.leftover_kg, mr.measurement_method, mr.notes
                FROM daily_mess_records mr
                JOIN mess_blocks b ON b.block_id = mr.block_id
                WHERE (? IS NULL OR INSTR(LOWER(b.block_name), LOWER(?)) > 0
                       OR INSTR(LOWER(NVL(mr.notes, ' ')), LOWER(?)) > 0)
                ORDER BY mr.record_date DESC, mr.record_id DESC
                FETCH FIRST 40 ROWS ONLY
                """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String query = blankToNull(search);
            statement.setString(1, query); statement.setString(2, query); statement.setString(3, query);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(row(rs, "record_id", "block_id", "block_name", "record_date",
                        "meals_served", "prepared_kg", "leftover_kg", "measurement_method", "notes"));
            }
        }
        return List.copyOf(result);
    }

    public List<Map<String, Object>> loadBlocks(Connection connection) throws SQLException {
        return queryRows(connection,
                "SELECT block_id, block_code, block_name FROM mess_blocks WHERE active_flag = 'Y' ORDER BY block_name",
                "block_id", "block_code", "block_name");
    }

    public List<Map<String, Object>> loadUsers(Connection connection, String search) throws SQLException {
        String sql = """
                SELECT user_id, username, role_code, active_flag, created_at, password_changed_at
                FROM app_users
                WHERE (? IS NULL OR INSTR(LOWER(username), LOWER(?)) > 0)
                ORDER BY CASE WHEN role_code = 'ADMIN' AND active_flag = 'Y' THEN 0 ELSE 1 END,
                         username, user_id
                FETCH FIRST 100 ROWS ONLY
                """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String query = blankToNull(search); statement.setString(1, query); statement.setString(2, query);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(row(rs, "user_id", "username", "role_code", "active_flag",
                        "created_at", "password_changed_at"));
            }
        }
        return List.copyOf(result);
    }

    /** Account detail deliberately excludes password hashes and profile/contact fields. */
    public Map<String, Object> loadUserDetails(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT user_id, username, role_code, active_flag, created_at, password_changed_at
                FROM app_users WHERE user_id = ?
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? row(rs, "user_id", "username", "role_code", "active_flag",
                        "created_at", "password_changed_at") : null;
            }
        }
    }

    public List<Map<String, Object>> loadInventory(Connection connection) throws SQLException {
        String sql = """
                SELECT b.block_name, i.ingredient_name, i.base_unit, ib.on_hand_qty,
                       ib.reorder_level, ib.updated_at
                FROM inventory_balances ib
                JOIN mess_blocks b ON b.block_id = ib.block_id
                JOIN ingredients i ON i.ingredient_id = ib.ingredient_id
                ORDER BY CASE WHEN ib.on_hand_qty < ib.reorder_level THEN 0 ELSE 1 END,
                         b.block_name, i.ingredient_name
                FETCH FIRST 100 ROWS ONLY
                """;
        return queryRows(connection, sql, "block_name", "ingredient_name", "base_unit", "on_hand_qty",
                "reorder_level", "updated_at");
    }

    public List<Map<String, Object>> loadTransactions(Connection connection) throws SQLException {
        String sql = """
                SELECT it.transaction_reference, it.block_id, b.block_name, i.ingredient_name, i.base_unit,
                       it.transaction_type, it.quantity_delta, it.balance_after_qty, it.created_at, it.notes
                FROM inventory_transactions it
                JOIN mess_blocks b ON b.block_id = it.block_id
                JOIN ingredients i ON i.ingredient_id = it.ingredient_id
                ORDER BY it.created_at DESC, it.transaction_id DESC
                FETCH FIRST 100 ROWS ONLY
                """;
        return queryRows(connection, sql, "transaction_reference", "block_id", "block_name", "ingredient_name",
                "base_unit", "transaction_type", "quantity_delta", "balance_after_qty", "created_at", "notes");
    }

    public Map<String, Object> loadOrder(Connection connection, long id) throws SQLException {
        String sql = """
                SELECT po.po_id, po.po_number, po.status, po.vendor_id, po.block_id, po.ordered_at,
                       po.expected_by, po.approved_amount, v.vendor_name, b.block_name,
                       (SELECT COUNT(*) FROM purchase_order_items poi WHERE poi.po_id = po.po_id) item_count,
                       (SELECT COUNT(*) FROM delivery_receipts dr WHERE dr.po_id = po.po_id) receipt_count,
                       (SELECT COUNT(*) FROM vendor_penalty_reviews pr WHERE pr.po_id = po.po_id) review_count,
                       (SELECT COUNT(*) FROM audit_log al WHERE al.table_name = 'PURCHASE_ORDERS'
                          AND al.record_key = TO_CHAR(po.po_id)) audit_count
                FROM purchase_orders po JOIN vendors v ON v.vendor_id = po.vendor_id
                JOIN mess_blocks b ON b.block_id = po.block_id WHERE po.po_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? row(rs, "po_id", "po_number", "status", "vendor_id", "block_id",
                        "ordered_at", "expected_by", "approved_amount", "vendor_name", "block_name",
                        "item_count", "receipt_count", "review_count", "audit_count") : null;
            }
        }
    }

    public Map<String, Object> loadDailyRecord(Connection connection, long id) throws SQLException {
        String sql = """
                SELECT record_id, block_id, record_date, meals_served, prepared_kg, leftover_kg,
                       measurement_method, notes
                FROM daily_mess_records WHERE record_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? row(rs, "record_id", "block_id", "record_date", "meals_served", "prepared_kg",
                        "leftover_kg", "measurement_method", "notes") : null;
            }
        }
    }

    public boolean receiptReversed(Connection connection, long receiptId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM audit_log
                WHERE table_name = 'DELIVERY_RECEIPTS' AND record_key = ?
                  AND DBMS_LOB.INSTR(new_values, 'INVENTORY_REVERSAL') > 0
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Long.toString(receiptId));
            try (ResultSet rs = statement.executeQuery()) { return rs.next() && rs.getLong(1) > 0; }
        }
    }

    public void deleteUnreceivedOrder(Connection connection, long id, String reason, String actor) throws SQLException, AdminException {
        String cleaned = reason(reason, 400);
        inTransaction(connection, () -> {
            Map<String, Object> order = lockOrder(connection, id);
            if (order == null) throw new AdminException("That purchase order no longer exists.");
            if (number(order, "receipt_count") > 0 || number(order, "review_count") > 0
                    || number(order, "audit_count") > 0) {
                throw new AdminException("This order has receipt, review, or audit history and cannot be deleted. Preserve it or cancel an open/partial order instead.");
            }
            try (PreparedStatement deleteItems = connection.prepareStatement("DELETE FROM purchase_order_items WHERE po_id = ?")) {
                deleteItems.setLong(1, id); deleteItems.executeUpdate();
            }
            try (PreparedStatement deleteOrder = connection.prepareStatement("DELETE FROM purchase_orders WHERE po_id = ?")) {
                deleteOrder.setLong(1, id);
                if (deleteOrder.executeUpdate() != 1) throw new AdminException("That purchase order no longer exists.");
            }
            audit(connection, "PURCHASE_ORDERS", Long.toString(id), "D", actor,
                    "po_number=" + order.get("po_number") + "; items=" + number(order, "item_count"),
                    "Deleted order and its unreferenced order items after dependency recheck; reason=" + cleaned);
            return null;
        });
    }

    public void deleteUnprocessedReceipt(Connection connection, long receiptId, String reason, String actor)
            throws SQLException, AdminException {
        String cleaned = reason(reason, 400);
        inTransaction(connection, () -> {
            Receipt receipt = lockReceipt(connection, receiptId);
            if (receipt == null) throw new AdminException("That goods receipt no longer exists.");
            if (receipt.verifiedAt() != null || receipt.inventoryProcessedAt() != null) {
                throw new AdminException("Verified receipt history is preserved. Use the compensating inventory-reversal action where available.");
            }
            long itemCount = count(connection, "SELECT COUNT(*) FROM delivery_receipt_items WHERE receipt_id = ?", receiptId);
            long auditCount;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM audit_log WHERE table_name='DELIVERY_RECEIPTS' AND record_key = ?")) {
                statement.setString(1, Long.toString(receiptId));
                try (ResultSet rs = statement.executeQuery()) { auditCount = rs.next() ? rs.getLong(1) : 0; }
            }
            if (auditCount > 0) throw new AdminException("Audit history already references this receipt. Keep it as historical evidence.");
            long ledgerCount = count(connection, """
                    SELECT COUNT(*) FROM inventory_transactions it
                    JOIN delivery_receipt_items dri ON dri.receipt_item_id=it.source_receipt_item_id
                    WHERE dri.receipt_id = ?
                    """, receiptId);
            if (ledgerCount > 0) throw new AdminException("Inventory ledger entries reference this receipt. Its evidence cannot be deleted.");
            try (PreparedStatement items = connection.prepareStatement("DELETE FROM delivery_receipt_items WHERE receipt_id = ?")) {
                items.setLong(1, receiptId);
                items.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM delivery_receipts WHERE receipt_id = ?")) {
                delete.setLong(1, receiptId);
                if (delete.executeUpdate() != 1) throw new AdminException("That goods receipt changed during this request.");
            }
            audit(connection, "DELIVERY_RECEIPTS", Long.toString(receiptId), "D", actor,
                    "unverified; inventory not processed; receipt_items=" + itemCount,
                    "Removed only an unverified receipt with no ledger references; reason=" + cleaned);
            return null;
        });
    }

    public void cancelOrder(Connection connection, long id, String reason, String actor) throws SQLException, AdminException {
        String cleaned = reason(reason, 400);
        inTransaction(connection, () -> {
            Map<String, Object> order = lockOrder(connection, id);
            if (order == null) throw new AdminException("That purchase order no longer exists.");
            String status = String.valueOf(order.get("status"));
            if (!("OPEN".equals(status) || "PARTIAL".equals(status))) {
                throw new AdminException("Only open or partial orders can be cancelled. Closed or already-cancelled history is preserved.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE purchase_orders SET status = 'CANCELLED' WHERE po_id = ? AND status IN ('OPEN','PARTIAL')")) {
                statement.setLong(1, id);
                if (statement.executeUpdate() != 1) throw new AdminException("The order changed during this request. Refresh and try again.");
            }
            audit(connection, "PURCHASE_ORDERS", Long.toString(id), "U", actor,
                    "status=" + status, "status=CANCELLED; reason=" + cleaned);
            return null;
        });
    }

    public void deleteDailyRecord(Connection connection, long id, String actor) throws SQLException, AdminException {
        inTransaction(connection, () -> {
            Map<String, Object> record = lockDaily(connection, id);
            if (record == null) throw new AdminException("That daily mess record no longer exists.");
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM daily_mess_records WHERE record_id = ?")) {
                statement.setLong(1, id);
                if (statement.executeUpdate() != 1) throw new AdminException("That daily mess record changed during this request.");
            }
            audit(connection, "DAILY_MESS_RECORDS", Long.toString(id), "D", actor,
                    dailyAuditValues(record),
                    "Deleted by administrator after an explicit row-level confirmation.");
            return null;
        });
    }

    public void saveDailyRecord(Connection connection, Long id, DailyDraft draft, String actor)
            throws SQLException, AdminException {
        validateDailyDraft(draft);
        inTransaction(connection, () -> {
            if (id == null) {
                try (PreparedStatement block = connection.prepareStatement("SELECT 1 FROM mess_blocks WHERE block_id = ? AND active_flag = 'Y'")) {
                    block.setLong(1, draft.blockId());
                    try (ResultSet rs = block.executeQuery()) { if (!rs.next()) throw new AdminException("Choose an active mess block."); }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO daily_mess_records
                            (block_id, record_date, meals_served, prepared_kg, leftover_kg, measurement_method, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    bindDaily(statement, draft);
                    statement.executeUpdate();
                    long createdId = findDailyId(connection, draft.blockId(), draft.recordDate());
                    audit(connection, "DAILY_MESS_RECORDS", Long.toString(createdId), "I", actor, null,
                            dailyAuditValues(draft));
                }
            } else {
                Map<String, Object> old = lockDaily(connection, id);
                if (old == null) throw new AdminException("That daily mess record no longer exists.");
                try (PreparedStatement block = connection.prepareStatement("SELECT 1 FROM mess_blocks WHERE block_id = ? AND active_flag = 'Y'")) {
                    block.setLong(1, draft.blockId());
                    try (ResultSet rs = block.executeQuery()) { if (!rs.next()) throw new AdminException("Choose an active mess block."); }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE daily_mess_records
                        SET block_id=?, record_date=?, meals_served=?, prepared_kg=?, leftover_kg=?,
                            measurement_method=?, notes=?
                        WHERE record_id=?
                        """)) {
                    bindDaily(statement, draft); statement.setLong(8, id);
                    if (statement.executeUpdate() != 1) throw new AdminException("That daily mess record changed during this request.");
                }
                audit(connection, "DAILY_MESS_RECORDS", Long.toString(id), "U", actor,
                        dailyAuditValues(old), dailyAuditValues(draft));
            }
            return null;
        });
    }

    public void updateUser(Connection connection, long targetId, String role, String active,
            long actorId, String actorName) throws SQLException, AdminException {
        if (!("ADMIN".equals(role) || "READ_ONLY".equals(role)) || !("Y".equals(active) || "N".equals(active))) {
            throw new AdminException("Choose a supported role and active status.");
        }
        inTransaction(connection, () -> {
            // Serialize role/status changes so two administrators cannot concurrently remove the last active admin.
            try (Statement lock = connection.createStatement()) { lock.execute("LOCK TABLE app_users IN EXCLUSIVE MODE"); }
            Map<String, Object> target = loadUser(connection, targetId, true);
            if (target == null) throw new AdminException("That account no longer exists.");
            String oldRole = String.valueOf(target.get("role_code"));
            String oldActive = String.valueOf(target.get("active_flag"));
            if (targetId == actorId && (!"ADMIN".equals(role) || !"Y".equals(active))) {
                throw new AdminException("You cannot demote or deactivate the account currently in use. Ask another administrator to review it.");
            }
            if ("ADMIN".equals(oldRole) && "Y".equals(oldActive)
                    && !("ADMIN".equals(role) && "Y".equals(active))) {
                if (countActiveAdmins(connection) <= 1) {
                    throw new AdminException("This is the last active administrator. Keep at least one active ADMIN account.");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE app_users SET role_code = ?, active_flag = ? WHERE user_id = ?")) {
                statement.setString(1, role); statement.setString(2, active); statement.setLong(3, targetId);
                if (statement.executeUpdate() != 1) throw new AdminException("That account changed during this request.");
            }
            audit(connection, "APP_USERS", Long.toString(targetId), "U", actorName,
                    "username=" + target.get("username") + "; role=" + oldRole + "; active=" + oldActive,
                    "role=" + role + "; active=" + active);
            return null;
        });
    }

    public void reverseReceiptInventory(Connection connection, long receiptId, String reason, String actor)
            throws SQLException, AdminException {
        String cleaned = reason(reason, 400);
        inTransaction(connection, () -> {
            Receipt receipt = lockReceipt(connection, receiptId);
            if (receipt == null) throw new AdminException("That goods receipt no longer exists.");
            if (receipt.inventoryProcessedAt() == null || receipt.verifiedAt() == null) {
                throw new AdminException("Only a verified receipt that posted accepted stock can be reversed. Receipt evidence is preserved.");
            }
            if (receiptReversed(connection, receiptId)) {
                throw new AdminException("Inventory for this receipt has already been reversed. The original receipt remains in history.");
            }
            List<ReceiptLine> lines = loadReceiptLines(connection, receiptId);
            for (ReceiptLine line : lines) {
                BigDecimal balance = lockBalance(connection, receipt.blockId(), line.ingredientId());
                if (balance == null || balance.compareTo(line.acceptedQty()) < 0) {
                    throw new AdminException("The current accepted stock balance is too low to reverse this receipt safely. No changes were made.");
                }
                BigDecimal after = balance.subtract(line.acceptedQty());
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE inventory_balances SET on_hand_qty = ?, updated_at = SYSTIMESTAMP
                        WHERE block_id = ? AND ingredient_id = ? AND on_hand_qty >= ?
                        """)) {
                    update.setBigDecimal(1, after); update.setLong(2, receipt.blockId());
                    update.setLong(3, line.ingredientId()); update.setBigDecimal(4, line.acceptedQty());
                    if (update.executeUpdate() != 1) throw new AdminException("The stock balance changed. No reversal was committed; refresh and review the inventory.");
                }
                if (line.acceptedQty().signum() > 0) {
                    String transactionReference = "REV-R" + receiptId + "-I" + line.receiptItemId();
                    String notes = "Admin reversal of receipt " + receiptId + "; reason: " + cleaned;
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO inventory_transactions
                                (transaction_reference, block_id, ingredient_id, transaction_type,
                                 quantity_delta, balance_after_qty, source_receipt_item_id, notes)
                            VALUES (?, ?, ?, 'ADJUSTMENT_OUT', ?, ?, NULL, ?)
                            """)) {
                        insert.setString(1, transactionReference); insert.setLong(2, receipt.blockId());
                        insert.setLong(3, line.ingredientId()); insert.setBigDecimal(4, line.acceptedQty().negate());
                        insert.setBigDecimal(5, after); insert.setString(6, notes);
                        insert.executeUpdate();
                    }
                }
            }
            audit(connection, "DELIVERY_RECEIPTS", Long.toString(receiptId), "U", actor,
                    "inventory_processed_at=" + receipt.inventoryProcessedAt(),
                    "INVENTORY_REVERSAL; reason=" + cleaned + "; accepted quantities compensated by ADJUSTMENT_OUT ledger entries.");
            return null;
        });
    }

    private Map<String, Object> lockOrder(Connection connection, long id) throws SQLException {
        Map<String, Object> order;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT po_id, po_number, status FROM purchase_orders WHERE po_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                order = rs.next() ? new LinkedHashMap<>(row(rs, "po_id", "po_number", "status")) : null;
            }
        }
        if (order == null) return null;
        order.put("item_count", count(connection, "SELECT COUNT(*) FROM purchase_order_items WHERE po_id = ?", id));
        order.put("receipt_count", count(connection, "SELECT COUNT(*) FROM delivery_receipts WHERE po_id = ?", id));
        order.put("review_count", count(connection, "SELECT COUNT(*) FROM vendor_penalty_reviews WHERE po_id = ?", id));
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM audit_log WHERE table_name='PURCHASE_ORDERS' AND record_key = ?
                """)) {
            statement.setString(1, Long.toString(id));
            try (ResultSet rs = statement.executeQuery()) { order.put("audit_count", rs.next() ? rs.getLong(1) : 0L); }
        }
        return order;
    }

    private long count(Connection connection, String sql, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
        }
    }

    private Map<String, Object> lockDaily(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT record_id, block_id, record_date, meals_served, prepared_kg, leftover_kg,
                       measurement_method, notes FROM daily_mess_records WHERE record_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? row(rs, "record_id", "block_id", "record_date", "meals_served",
                        "prepared_kg", "leftover_kg", "measurement_method", "notes") : null;
            }
        }
    }

    private Map<String, Object> loadUser(Connection connection, long id, boolean forUpdate) throws SQLException {
        String sql = "SELECT user_id, username, role_code, active_flag FROM app_users WHERE user_id = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? row(rs, "user_id", "username", "role_code", "active_flag") : null;
            }
        }
    }

    private long countActiveAdmins(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM app_users WHERE role_code='ADMIN' AND active_flag='Y'");
             ResultSet rs = statement.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
    }

    private Receipt lockReceipt(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT dr.receipt_id, dr.po_id, po.block_id, dr.verified_at, dr.inventory_processed_at
                FROM delivery_receipts dr JOIN purchase_orders po ON po.po_id = dr.po_id
                WHERE dr.receipt_id = ? FOR UPDATE OF dr.receipt_id
                """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? new Receipt(rs.getLong("receipt_id"), rs.getLong("po_id"),
                        rs.getLong("block_id"), rs.getTimestamp("verified_at"),
                        rs.getTimestamp("inventory_processed_at")) : null;
            }
        }
    }

    private List<ReceiptLine> loadReceiptLines(Connection connection, long receiptId) throws SQLException {
        List<ReceiptLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT dri.receipt_item_id, poi.ingredient_id, dri.accepted_qty
                FROM delivery_receipt_items dri
                JOIN purchase_order_items poi ON poi.po_id = dri.po_id AND poi.po_item_id = dri.po_item_id
                WHERE dri.receipt_id = ? AND dri.accepted_qty > 0
                ORDER BY poi.ingredient_id, dri.receipt_item_id
                """)) {
            statement.setLong(1, receiptId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) lines.add(new ReceiptLine(rs.getLong("receipt_item_id"),
                        rs.getLong("ingredient_id"), rs.getBigDecimal("accepted_qty")));
            }
        }
        return List.copyOf(lines);
    }

    private BigDecimal lockBalance(Connection connection, long blockId, long ingredientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT on_hand_qty FROM inventory_balances WHERE block_id = ? AND ingredient_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, blockId); statement.setLong(2, ingredientId);
            try (ResultSet rs = statement.executeQuery()) { return rs.next() ? rs.getBigDecimal(1) : null; }
        }
    }

    static void validateDailyDraft(DailyDraft draft) throws AdminException {
        if (draft.blockId() <= 0 || draft.recordDate() == null || draft.mealsServed() < 0
                || draft.mealsServed() > 99_999_999) throw new AdminException("Provide a valid block, date, and meal count.");
        validateQuantity(draft.preparedKg(), "Prepared quantity");
        validateQuantity(draft.leftoverKg(), "Measured leftovers");
        if (draft.preparedKg() != null && draft.leftoverKg() != null
                && draft.leftoverKg().compareTo(draft.preparedKg()) > 0) {
            throw new AdminException("Measured leftovers cannot exceed the recorded prepared quantity.");
        }
        if (draft.leftoverKg() != null && (draft.measurementMethod() == null || draft.measurementMethod().isBlank())) {
            throw new AdminException("Describe how measured leftovers were recorded.");
        }
        if (length(draft.measurementMethod()) > 200 || length(draft.notes()) > 500) {
            throw new AdminException("Measurement method must be at most 200 characters and notes at most 500 characters.");
        }
    }

    private static void validateQuantity(BigDecimal quantity, String label) throws AdminException {
        if (quantity == null) return;
        BigDecimal normalized = quantity.stripTrailingZeros();
        if (quantity.signum() < 0 || normalized.scale() > 3 || normalized.precision() > 12
                || normalized.precision() - normalized.scale() > 9) {
            throw new AdminException(label + " must be non-negative and fit NUMBER(12,3).");
        }
    }

    private void bindDaily(PreparedStatement statement, DailyDraft draft) throws SQLException {
        statement.setLong(1, draft.blockId()); statement.setDate(2, Date.valueOf(draft.recordDate()));
        statement.setInt(3, draft.mealsServed()); setDecimal(statement, 4, draft.preparedKg());
        setDecimal(statement, 5, draft.leftoverKg()); setStringOrNull(statement, 6, draft.measurementMethod());
        setStringOrNull(statement, 7, draft.notes());
    }

    private void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.NUMERIC); else statement.setBigDecimal(index, value);
    }

    private void setStringOrNull(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) statement.setNull(index, java.sql.Types.VARCHAR);
        else statement.setString(index, value.trim());
    }

    private long findDailyId(Connection connection, long blockId, java.time.LocalDate recordDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT record_id FROM daily_mess_records WHERE block_id = ? AND record_date = ?")) {
            statement.setLong(1, blockId);
            statement.setDate(2, Date.valueOf(recordDate));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("Daily mess record insert could not be identified after creation.");
    }

    private void audit(Connection connection, String table, String recordKey, String operation,
            String actor, String oldValues, String newValues) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_log (table_name, record_key, operation_type, changed_by, old_values, new_values)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, table); statement.setString(2, recordKey); statement.setString(3, operation);
            statement.setString(4, actor);
            if (oldValues == null) statement.setNull(5, java.sql.Types.CLOB); else statement.setString(5, oldValues);
            if (newValues == null) statement.setNull(6, java.sql.Types.CLOB); else statement.setString(6, newValues);
            statement.executeUpdate();
        }
    }

    private <T> T inTransaction(Connection connection, Work<T> work) throws SQLException, AdminException {
        connection.setAutoCommit(false);
        try {
            T result = work.run(); connection.commit(); return result;
        } catch (SQLException | AdminException | RuntimeException exception) {
            try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
            throw exception;
        }
    }

    private List<Map<String, Object>> queryRows(Connection connection, String sql, String... columns) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) result.add(row(rs, columns));
        }
        return List.copyOf(result);
    }

    Map<String, Object> row(ResultSet rs, String... columns) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        ResultSetMetaData metadata = rs.getMetaData();
        for (String column : columns) {
            int index = rs.findColumn(column);
            int sqlType = metadata.getColumnType(index);
            String typeName = metadata.getColumnTypeName(index);
            if (isDateTimeType(sqlType, typeName)) {
                // getTimestamp returns a java.sql.Timestamp (or null), unlike Oracle's vendor type from getObject.
                // Use it for Oracle DATE too: Oracle DATE can contain a time component.
                values.put(column, rs.getTimestamp(column));
            } else {
                values.put(column, rs.getObject(column));
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private boolean isDateTimeType(int sqlType, String typeName) {
        if (sqlType == Types.DATE || sqlType == Types.TIMESTAMP
                || sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
            return true;
        }
        return typeName != null && typeName.toUpperCase(Locale.ROOT).startsWith("TIMESTAMP");
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private long number(Map<String, Object> map, String key) { return ((Number) map.get(key)).longValue(); }
    private String reason(String value, int max) throws AdminException {
        String result = value == null ? "" : value.trim();
        if (result.isBlank() || length(result) > max || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new AdminException("Enter a clear reason of no more than " + max + " characters.");
        }
        return result;
    }
    private static int length(String value) { return value == null ? 0 : value.codePointCount(0, value.length()); }

    private String dailyAuditValues(DailyDraft draft) {
        return "block_id=" + draft.blockId() + "; date=" + draft.recordDate()
                + "; meals_served=" + draft.mealsServed() + "; prepared_kg=" + draft.preparedKg()
                + "; leftover_kg=" + draft.leftoverKg() + "; measurement_method=" + draft.measurementMethod();
    }

    private String dailyAuditValues(Map<String, Object> values) {
        return "block_id=" + values.get("block_id") + "; date=" + values.get("record_date")
                + "; meals_served=" + values.get("meals_served") + "; prepared_kg=" + values.get("prepared_kg")
                + "; leftover_kg=" + values.get("leftover_kg") + "; measurement_method=" + values.get("measurement_method");
    }

    public record Overview(long vendors, long ingredients, long blocks, long orders, long receipts,
            long balances, long transactions, long dailyRecords, long users, long contractRules, long penaltyReviews,
            long auditEvents) {
        public long getVendors() { return vendors; }
        public long getIngredients() { return ingredients; }
        public long getBlocks() { return blocks; }
        public long getOrders() { return orders; }
        public long getReceipts() { return receipts; }
        public long getBalances() { return balances; }
        public long getTransactions() { return transactions; }
        public long getDailyRecords() { return dailyRecords; }
        public long getUsers() { return users; }
        public long getContractRules() { return contractRules; }
        public long getPenaltyReviews() { return penaltyReviews; }
        public long getAuditEvents() { return auditEvents; }
    }
    public record DailyDraft(long blockId, java.time.LocalDate recordDate, int mealsServed,
            BigDecimal preparedKg, BigDecimal leftoverKg, String measurementMethod, String notes) { }
    public static final class AdminException extends Exception {
        private static final long serialVersionUID = 1L;
        public AdminException(String message) { super(message); }
    }
    private record Receipt(long id, long poId, long blockId, Timestamp verifiedAt, Timestamp inventoryProcessedAt) { }
    private record ReceiptLine(long receiptItemId, long ingredientId, BigDecimal acceptedQty) { }
    private interface Work<T> { T run() throws SQLException, AdminException; }
}
