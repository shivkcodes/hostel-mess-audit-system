package edu.hostel.mess.web.audit;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only vendor performance summaries and supporting order/receipt evidence. */
public final class VendorAuditRepository {
    private static final String VENDORS_QUERY = """
            SELECT vendor_id, vendor_code, vendor_name, active_flag
            FROM vendors
            ORDER BY vendor_name, vendor_code, vendor_id
            """;

    private static final String VENDOR_SUMMARY_START = """
            SELECT v.vendor_id, v.vendor_code, v.vendor_name, v.active_flag,
                   COUNT(DISTINCT po.po_id) AS order_count,
                   COUNT(DISTINCT CASE WHEN po.status = 'OPEN' THEN po.po_id END) AS open_order_count,
                   COUNT(DISTINCT CASE WHEN po.status = 'PARTIAL' THEN po.po_id END) AS partial_order_count,
                   COUNT(DISTINCT CASE WHEN po.status = 'CLOSED' THEN po.po_id END) AS closed_order_count,
                   COUNT(DISTINCT CASE WHEN po.status = 'CANCELLED' THEN po.po_id END) AS cancelled_order_count,
                   COUNT(DISTINCT dr.receipt_id) AS receipt_count,
                   COUNT(DISTINCT CASE WHEN dr.verified_at IS NOT NULL THEN dr.receipt_id END) AS verified_receipt_count,
                   COUNT(DISTINCT CASE WHEN dr.receipt_id IS NOT NULL AND dr.verified_at IS NULL THEN dr.receipt_id END) AS unverified_receipt_count
            FROM vendors v
            LEFT JOIN purchase_orders po ON po.vendor_id = v.vendor_id
            """;

    private static final String VERIFIED_TOTALS = """
            WITH verified_receipt_totals AS (
                SELECT dri.po_id, dri.po_item_id,
                       SUM(dri.received_qty) AS received_qty,
                       SUM(dri.accepted_qty) AS accepted_qty,
                       SUM(dri.rejected_qty) AS rejected_qty
                FROM delivery_receipt_items dri
                JOIN delivery_receipts dr
                  ON dr.receipt_id = dri.receipt_id AND dr.po_id = dri.po_id
                WHERE dr.verified_at IS NOT NULL
                GROUP BY dri.po_id, dri.po_item_id
            )
            """;

    private static final String QUANTITY_SUMMARY_START = VERIFIED_TOTALS + """
            SELECT v.vendor_id, v.vendor_code, v.vendor_name, i.base_unit,
                   COUNT(DISTINCT po.po_id) AS order_count,
                   COUNT(poi.po_item_id) AS line_count,
                   NVL(SUM(poi.ordered_qty), 0) AS ordered_qty,
                   NVL(SUM(NVL(vrt.received_qty, 0)), 0) AS received_qty,
                   NVL(SUM(NVL(vrt.accepted_qty, 0)), 0) AS accepted_qty,
                   NVL(SUM(NVL(vrt.rejected_qty, 0)), 0) AS rejected_qty,
                   NVL(SUM(GREATEST(poi.ordered_qty - NVL(vrt.received_qty, 0), 0)), 0) AS short_qty,
                   CASE WHEN SUM(NVL(vrt.received_qty, 0)) > 0
                        THEN ROUND(100 * SUM(NVL(vrt.accepted_qty, 0))
                                   / SUM(NVL(vrt.received_qty, 0)), 2)
                        ELSE NULL END AS accepted_share_percent
            FROM vendors v
            JOIN purchase_orders po ON po.vendor_id = v.vendor_id
            JOIN purchase_order_items poi ON poi.po_id = po.po_id
            JOIN ingredients i ON i.ingredient_id = poi.ingredient_id
            LEFT JOIN verified_receipt_totals vrt
              ON vrt.po_id = poi.po_id AND vrt.po_item_id = poi.po_item_id
            WHERE po.status <> 'CANCELLED'
            """;

    private static final String ORDER_QUERY = """
            SELECT po.po_id, po.po_number, po.ordered_at, po.expected_by, po.status,
                   COUNT(DISTINCT poi.po_item_id) AS item_count,
                   COUNT(DISTINCT dr.receipt_id) AS receipt_count,
                   COUNT(DISTINCT CASE WHEN dr.verified_at IS NOT NULL THEN dr.receipt_id END) AS verified_receipt_count,
                   COUNT(DISTINCT CASE WHEN dr.receipt_id IS NOT NULL AND dr.verified_at IS NULL THEN dr.receipt_id END) AS unverified_receipt_count
            FROM purchase_orders po
            LEFT JOIN purchase_order_items poi ON poi.po_id = po.po_id
            LEFT JOIN delivery_receipts dr ON dr.po_id = po.po_id
            WHERE po.vendor_id = ?
            """;

    private static final String ORDER_LINE_QUERY = VERIFIED_TOTALS + """
            SELECT po.po_id, po.po_number, po.status, poi.po_item_id,
                   i.ingredient_name, i.base_unit, poi.ordered_qty,
                   NVL(vrt.received_qty, 0) AS received_qty,
                   NVL(vrt.accepted_qty, 0) AS accepted_qty,
                   NVL(vrt.rejected_qty, 0) AS rejected_qty,
                   CASE WHEN po.status = 'CANCELLED' THEN NULL
                        ELSE GREATEST(poi.ordered_qty - NVL(vrt.received_qty, 0), 0)
                   END AS short_qty
            FROM purchase_orders po
            JOIN purchase_order_items poi ON poi.po_id = po.po_id
            JOIN ingredients i ON i.ingredient_id = poi.ingredient_id
            LEFT JOIN verified_receipt_totals vrt
              ON vrt.po_id = poi.po_id AND vrt.po_item_id = poi.po_item_id
            WHERE po.vendor_id = ?
            """;

    private static final String RECEIPT_EVIDENCE_QUERY = """
            SELECT po.po_number, dr.receipt_id, dr.arrived_at, dr.verified_at,
                   dri.receipt_item_id, i.ingredient_name, i.base_unit,
                   dri.received_qty, dri.accepted_qty, dri.rejected_qty
            FROM purchase_orders po
            JOIN delivery_receipts dr ON dr.po_id = po.po_id
            LEFT JOIN delivery_receipt_items dri
              ON dri.receipt_id = dr.receipt_id AND dri.po_id = dr.po_id
            LEFT JOIN purchase_order_items poi
              ON poi.po_id = dri.po_id AND poi.po_item_id = dri.po_item_id
            LEFT JOIN ingredients i ON i.ingredient_id = poi.ingredient_id
            WHERE po.vendor_id = ?
            """;

    public PageData loadPage(Connection connection, Filters filters) throws SQLException {
        List<Map<String, Object>> vendors = loadVendors(connection);
        List<Map<String, Object>> vendorSummaries = loadVendorSummaries(connection, filters);
        List<Map<String, Object>> quantitySummaries = loadQuantitySummaries(connection, filters);
        Map<String, Object> selectedVendor = findVendor(vendors, filters.vendorId());
        List<Map<String, Object>> orders = List.of();
        List<Map<String, Object>> orderLines = List.of();
        List<Map<String, Object>> receiptEvidence = List.of();
        if (selectedVendor != null) {
            orders = loadOrders(connection, filters);
            orderLines = loadOrderLines(connection, filters);
            receiptEvidence = loadReceiptEvidence(connection, filters);
        }
        return new PageData(vendors, summarize(vendorSummaries), vendorSummaries,
                quantitySummaries, selectedVendor, orders, orderLines, receiptEvidence, filters);
    }

    private List<Map<String, Object>> loadVendors(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(VENDORS_QUERY);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("vendorId", rs.getLong("vendor_id"));
                row.put("vendorCode", rs.getString("vendor_code"));
                row.put("vendorName", rs.getString("vendor_name"));
                row.put("activeFlag", rs.getString("active_flag"));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadVendorSummaries(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder(VENDOR_SUMMARY_START);
        List<Object> parameters = new ArrayList<>();
        appendDateFilters(sql, parameters, "po", filters);
        sql.append(" LEFT JOIN delivery_receipts dr ON dr.po_id = po.po_id WHERE 1 = 1");
        if (filters.vendorId() != null) {
            sql.append(" AND v.vendor_id = ?");
            parameters.add(filters.vendorId());
        }
        sql.append(" GROUP BY v.vendor_id, v.vendor_code, v.vendor_name, v.active_flag")
                .append(" ORDER BY v.vendor_name, v.vendor_code, v.vendor_id");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("vendorId", rs.getLong("vendor_id"));
                    row.put("vendorCode", rs.getString("vendor_code"));
                    row.put("vendorName", rs.getString("vendor_name"));
                    row.put("activeFlag", rs.getString("active_flag"));
                    row.put("orderCount", rs.getLong("order_count"));
                    row.put("openOrderCount", rs.getLong("open_order_count"));
                    row.put("partialOrderCount", rs.getLong("partial_order_count"));
                    row.put("closedOrderCount", rs.getLong("closed_order_count"));
                    row.put("cancelledOrderCount", rs.getLong("cancelled_order_count"));
                    row.put("receiptCount", rs.getLong("receipt_count"));
                    row.put("verifiedReceiptCount", rs.getLong("verified_receipt_count"));
                    row.put("unverifiedReceiptCount", rs.getLong("unverified_receipt_count"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadQuantitySummaries(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder(QUANTITY_SUMMARY_START);
        List<Object> parameters = new ArrayList<>();
        appendWhereFilters(sql, parameters, "po", "v", filters);
        sql.append(" GROUP BY v.vendor_id, v.vendor_code, v.vendor_name, i.base_unit")
                .append(" ORDER BY v.vendor_name, v.vendor_code, i.base_unit");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("vendorId", rs.getLong("vendor_id"));
                    row.put("vendorCode", rs.getString("vendor_code"));
                    row.put("vendorName", rs.getString("vendor_name"));
                    row.put("baseUnit", rs.getString("base_unit"));
                    row.put("orderCount", rs.getLong("order_count"));
                    row.put("lineCount", rs.getLong("line_count"));
                    row.put("orderedQty", rs.getBigDecimal("ordered_qty"));
                    row.put("receivedQty", rs.getBigDecimal("received_qty"));
                    row.put("acceptedQty", rs.getBigDecimal("accepted_qty"));
                    row.put("rejectedQty", rs.getBigDecimal("rejected_qty"));
                    row.put("shortQty", rs.getBigDecimal("short_qty"));
                    row.put("acceptedSharePercent", rs.getBigDecimal("accepted_share_percent"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadOrders(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder(ORDER_QUERY);
        List<Object> parameters = new ArrayList<>();
        parameters.add(filters.vendorId());
        appendDateFilters(sql, parameters, "po", filters);
        sql.append(" GROUP BY po.po_id, po.po_number, po.ordered_at, po.expected_by, po.status")
                .append(" ORDER BY po.ordered_at DESC, po.po_id DESC");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("poId", rs.getLong("po_id"));
                    row.put("poNumber", rs.getString("po_number"));
                    row.put("orderedAt", rs.getDate("ordered_at"));
                    row.put("expectedBy", rs.getDate("expected_by"));
                    row.put("status", rs.getString("status"));
                    row.put("itemCount", rs.getLong("item_count"));
                    row.put("receiptCount", rs.getLong("receipt_count"));
                    row.put("verifiedReceiptCount", rs.getLong("verified_receipt_count"));
                    row.put("unverifiedReceiptCount", rs.getLong("unverified_receipt_count"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadOrderLines(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder(ORDER_LINE_QUERY);
        List<Object> parameters = new ArrayList<>();
        parameters.add(filters.vendorId());
        appendDateFilters(sql, parameters, "po", filters);
        sql.append(" ORDER BY po.ordered_at DESC, po.po_number, i.ingredient_name, poi.po_item_id");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("poId", rs.getLong("po_id"));
                    row.put("poNumber", rs.getString("po_number"));
                    row.put("status", rs.getString("status"));
                    row.put("poItemId", rs.getLong("po_item_id"));
                    row.put("ingredientName", rs.getString("ingredient_name"));
                    row.put("baseUnit", rs.getString("base_unit"));
                    row.put("orderedQty", rs.getBigDecimal("ordered_qty"));
                    row.put("receivedQty", rs.getBigDecimal("received_qty"));
                    row.put("acceptedQty", rs.getBigDecimal("accepted_qty"));
                    row.put("rejectedQty", rs.getBigDecimal("rejected_qty"));
                    row.put("shortQty", rs.getBigDecimal("short_qty"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadReceiptEvidence(Connection connection, Filters filters)
            throws SQLException {
        StringBuilder sql = new StringBuilder(RECEIPT_EVIDENCE_QUERY);
        List<Object> parameters = new ArrayList<>();
        parameters.add(filters.vendorId());
        appendDateFilters(sql, parameters, "po", filters);
        sql.append(" ORDER BY po.ordered_at DESC, dr.arrived_at DESC, dr.receipt_id, i.ingredient_name");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("poNumber", rs.getString("po_number"));
                    row.put("receiptId", rs.getLong("receipt_id"));
                    row.put("arrivedAt", rs.getTimestamp("arrived_at"));
                    row.put("verifiedAt", rs.getTimestamp("verified_at"));
                    row.put("receiptItemId", nullableLong(rs, "receipt_item_id"));
                    row.put("ingredientName", rs.getString("ingredient_name"));
                    row.put("baseUnit", rs.getString("base_unit"));
                    row.put("receivedQty", rs.getBigDecimal("received_qty"));
                    row.put("acceptedQty", rs.getBigDecimal("accepted_qty"));
                    row.put("rejectedQty", rs.getBigDecimal("rejected_qty"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private void appendDateFilters(StringBuilder sql, List<Object> parameters,
            String orderAlias, Filters filters) {
        if (filters.fromDate() != null) {
            sql.append(" AND ").append(orderAlias).append(".ordered_at >= ?");
            parameters.add(Date.valueOf(filters.fromDate()));
        }
        if (filters.toDate() != null) {
            sql.append(" AND ").append(orderAlias).append(".ordered_at < ? + 1");
            parameters.add(Date.valueOf(filters.toDate()));
        }
    }

    private void appendWhereFilters(StringBuilder sql, List<Object> parameters,
            String orderAlias, String vendorAlias, Filters filters) {
        if (filters.fromDate() != null) {
            sql.append(" AND ").append(orderAlias).append(".ordered_at >= ?");
            parameters.add(Date.valueOf(filters.fromDate()));
        }
        if (filters.toDate() != null) {
            sql.append(" AND ").append(orderAlias).append(".ordered_at < ? + 1");
            parameters.add(Date.valueOf(filters.toDate()));
        }
        if (filters.vendorId() != null) {
            sql.append(" AND ").append(vendorAlias).append(".vendor_id = ?");
            parameters.add(filters.vendorId());
        }
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object parameter = parameters.get(index);
            if (parameter instanceof Date date) {
                statement.setDate(index + 1, date);
            } else if (parameter instanceof Long id) {
                statement.setLong(index + 1, id);
            } else {
                throw new SQLException("Unsupported vendor audit query parameter.");
            }
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Map<String, Object> findVendor(List<Map<String, Object>> vendors, Long id) {
        if (id == null) return null;
        for (Map<String, Object> vendor : vendors) {
            if (((Number) vendor.get("vendorId")).longValue() == id) return vendor;
        }
        return null;
    }

    private Map<String, Object> summarize(List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vendorCount", (long) rows.size());
        for (String key : List.of("orderCount", "openOrderCount", "partialOrderCount",
                "closedOrderCount", "cancelledOrderCount", "verifiedReceiptCount",
                "unverifiedReceiptCount")) {
            long total = 0;
            for (Map<String, Object> row : rows) total += ((Number) row.get(key)).longValue();
            result.put(key, total);
        }
        return result;
    }

    public record Filters(LocalDate fromDate, LocalDate toDate, Long vendorId) { }

    public record PageData(List<Map<String, Object>> vendors,
            Map<String, Object> summary,
            List<Map<String, Object>> vendorSummaries,
            List<Map<String, Object>> quantitySummaries,
            Map<String, Object> selectedVendor,
            List<Map<String, Object>> orders,
            List<Map<String, Object>> orderLines,
            List<Map<String, Object>> receiptEvidence,
            Filters filters) {
        public PageData {
            vendors = List.copyOf(vendors);
            summary = Collections.unmodifiableMap(new LinkedHashMap<>(summary));
            vendorSummaries = List.copyOf(vendorSummaries);
            quantitySummaries = List.copyOf(quantitySummaries);
            selectedVendor = selectedVendor == null ? null
                    : Collections.unmodifiableMap(new LinkedHashMap<>(selectedVendor));
            orders = List.copyOf(orders);
            orderLines = List.copyOf(orderLines);
            receiptEvidence = List.copyOf(receiptEvidence);
        }
    }
}
