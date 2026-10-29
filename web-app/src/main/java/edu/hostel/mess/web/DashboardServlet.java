package edu.hostel.mess.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.db.SafeSqlDiagnostics;
import edu.hostel.mess.web.profile.ProfileSaveOutcome;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/dashboard.jsp";
    private static final Logger LOGGER = Logger.getLogger(DashboardServlet.class.getName());

    private static final String SUMMARY_QUERY = """
            SELECT (SELECT COUNT(*) FROM vendors) AS vendor_count,
                   (SELECT COUNT(*) FROM purchase_orders) AS purchase_order_count,
                   (SELECT COUNT(*) FROM ingredients) AS ingredient_count,
                   (SELECT COUNT(*) FROM delivery_receipts) AS goods_receipt_count
            FROM dual
            """;

    private static final String QUANTITY_QUERY = """
            WITH verified_delivery_totals AS (
                SELECT dri.po_item_id,
                       SUM(dri.received_qty) AS received_qty,
                       SUM(dri.accepted_qty) AS accepted_qty,
                       SUM(dri.rejected_qty) AS rejected_qty
                FROM delivery_receipt_items dri
                JOIN delivery_receipts dr ON dr.receipt_id = dri.receipt_id
                WHERE dr.verified_at IS NOT NULL
                GROUP BY dri.po_item_id
            )
            SELECT i.base_unit,
                   NVL(SUM(poi.ordered_qty), 0) AS ordered_qty,
                   NVL(SUM(NVL(vdt.received_qty, 0)), 0) AS received_qty,
                   NVL(SUM(NVL(vdt.accepted_qty, 0)), 0) AS accepted_qty,
                   NVL(SUM(NVL(vdt.rejected_qty, 0)), 0) AS rejected_qty,
                   NVL(SUM(GREATEST(poi.ordered_qty - NVL(vdt.received_qty, 0), 0)), 0) AS short_qty
            FROM purchase_orders po
            JOIN purchase_order_items poi ON poi.po_id = po.po_id
            JOIN ingredients i ON i.ingredient_id = poi.ingredient_id
            LEFT JOIN verified_delivery_totals vdt ON vdt.po_item_id = poi.po_item_id
            WHERE po.status <> 'CANCELLED'
            GROUP BY i.base_unit
            ORDER BY i.base_unit
            """;

    private static final String LOW_STOCK_COUNT_QUERY = """
            SELECT COUNT(*)
            FROM inventory_balances
            WHERE on_hand_qty < reorder_level
            """;

    private static final String LOW_STOCK_ROWS_QUERY = """
            SELECT b.block_name,
                   i.ingredient_name,
                   i.base_unit,
                   ib.on_hand_qty,
                   ib.reorder_level
            FROM inventory_balances ib
            JOIN mess_blocks b ON b.block_id = ib.block_id
            JOIN ingredients i ON i.ingredient_id = ib.ingredient_id
            WHERE ib.on_hand_qty < ib.reorder_level
            ORDER BY b.block_name, i.ingredient_name
            FETCH FIRST 8 ROWS ONLY
            """;

    private static final String RECENT_MESS_QUERY = """
            SELECT b.block_name,
                   d.record_date,
                   d.meals_served,
                   d.prepared_kg,
                   d.leftover_kg,
                   d.measurement_method,
                   CASE
                       WHEN d.leftover_kg IS NOT NULL AND d.meals_served > 0
                       THEN ROUND(d.leftover_kg / NULLIF(d.meals_served, 0), 3)
                       ELSE NULL
                   END AS per_capita_leftover_kg
            FROM daily_mess_records d
            JOIN mess_blocks b ON b.block_id = d.block_id
            ORDER BY d.record_date DESC, d.record_id DESC
            FETCH FIRST 8 ROWS ONLY
            """;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String phase = "connection establishment";
        try (Connection connection = OracleConnectionFactory.openConnection()) {
            phase = "dashboard SQL execution and result retrieval";
            loadSummary(connection, request);
            request.setAttribute("quantityRows", loadQuantityRows(connection));
            request.setAttribute("lowStockCount", loadLowStockCount(connection));
            request.setAttribute("lowStockRows", loadLowStockRows(connection));
            request.setAttribute("recentMessRows", loadRecentMessRows(connection));
            request.setAttribute("dashboardAvailable", Boolean.TRUE);
            phase = "connection cleanup";
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Dashboard unavailable because required database settings are missing.");
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            logSqlDiagnostics(phase, exception);
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object flash = session.getAttribute(ProfileSaveOutcome.SESSION_ATTRIBUTE);
            if (flash instanceof String code) {
                String message = ProfileSaveOutcome.dashboardMessage(code);
                if (message != null) request.setAttribute("profileFlashMessage", message);
                session.removeAttribute(ProfileSaveOutcome.SESSION_ATTRIBUTE);
            }
        }
        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    private void loadSummary(Connection connection, HttpServletRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SUMMARY_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Dashboard summary query returned no row.");
            }
            request.setAttribute("vendorCount", resultSet.getLong("vendor_count"));
            request.setAttribute("purchaseOrderCount", resultSet.getLong("purchase_order_count"));
            request.setAttribute("ingredientCount", resultSet.getLong("ingredient_count"));
            request.setAttribute("goodsReceiptCount", resultSet.getLong("goods_receipt_count"));
        }
    }

    private List<Map<String, Object>> loadQuantityRows(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(QUANTITY_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("baseUnit", resultSet.getString("base_unit"));
                row.put("orderedQty", resultSet.getBigDecimal("ordered_qty"));
                row.put("receivedQty", resultSet.getBigDecimal("received_qty"));
                row.put("acceptedQty", resultSet.getBigDecimal("accepted_qty"));
                row.put("rejectedQty", resultSet.getBigDecimal("rejected_qty"));
                row.put("shortQty", resultSet.getBigDecimal("short_qty"));
                rows.add(row);
            }
        }
        return rows;
    }

    private long loadLowStockCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOW_STOCK_COUNT_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Low-stock count query returned no row.");
            }
            return resultSet.getLong(1);
        }
    }

    private List<Map<String, Object>> loadLowStockRows(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOW_STOCK_ROWS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("blockName", resultSet.getString("block_name"));
                row.put("ingredientName", resultSet.getString("ingredient_name"));
                row.put("baseUnit", resultSet.getString("base_unit"));
                row.put("onHandQty", resultSet.getBigDecimal("on_hand_qty"));
                row.put("reorderLevel", resultSet.getBigDecimal("reorder_level"));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> loadRecentMessRows(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(RECENT_MESS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("blockName", resultSet.getString("block_name"));
                row.put("recordDate", resultSet.getDate("record_date"));
                row.put("mealsServed", resultSet.getLong("meals_served"));
                row.put("preparedKg", resultSet.getBigDecimal("prepared_kg"));
                row.put("leftoverKg", resultSet.getBigDecimal("leftover_kg"));
                row.put("measurementMethod", resultSet.getString("measurement_method"));
                row.put("perCapitaLeftoverKg", resultSet.getBigDecimal("per_capita_leftover_kg"));
                rows.add(row);
            }
        }
        return rows;
    }

    private void setUnavailableState(HttpServletRequest request) {
        request.setAttribute("dashboardAvailable", Boolean.FALSE);
        request.setAttribute("dashboardError",
                "Dashboard data is temporarily unavailable. Review safe diagnostics in the Tomcat console.");
        request.setAttribute("quantityRows", List.of());
        request.setAttribute("lowStockRows", List.of());
        request.setAttribute("recentMessRows", List.of());
    }

    private void logSqlDiagnostics(String phase, SQLException exception) {
        SafeSqlDiagnostics.log(LOGGER, "DashboardServlet", phase, exception);
    }
}
