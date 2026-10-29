package edu.hostel.mess.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.analytics.AnalyticsJson;
import edu.hostel.mess.web.inventory.InventoryRepository;
import edu.hostel.mess.web.inventory.InventoryRepository.Filters;
import edu.hostel.mess.web.inventory.InventoryRepository.PageData;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/inventory")
public class InventoryServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/inventory.jsp";
    private static final Logger LOGGER = Logger.getLogger(InventoryServlet.class.getName());
    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "OPENING", "RECEIPT", "ISSUE", "ADJUSTMENT_IN", "ADJUSTMENT_OUT");
    private static final int STOCK_CHART_LIMIT = 12;
    private final InventoryRepository repository = new InventoryRepository();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Long blockId = parseIdFilter(request.getParameter("blockId"), "mess block", request);
        Long ingredientId = parseIdFilter(request.getParameter("ingredientId"), "ingredient", request);
        String transactionType = parseTransactionType(request.getParameter("transactionType"), request);
        request.setAttribute("selectedBlockId", blockId == null ? "" : blockId.toString());
        request.setAttribute("selectedIngredientId",
                ingredientId == null ? "" : ingredientId.toString());
        request.setAttribute("selectedTransactionType",
                transactionType == null ? "" : transactionType);
        request.setAttribute("transactionTypes", List.of(
                "OPENING", "RECEIPT", "ISSUE", "ADJUSTMENT_IN", "ADJUSTMENT_OUT"));
        request.setAttribute("inventoryChartJson", "[]");
        request.setAttribute("inventoryAnalyticsAvailable", Boolean.FALSE);

        try (Connection connection = OracleConnectionFactory.openConnection()) {
            PageData pageData = repository.loadPage(connection,
                    new Filters(blockId, ingredientId, transactionType));
            request.setAttribute("blocks", pageData.blocks());
            request.setAttribute("ingredients", pageData.ingredients());
            request.setAttribute("stockRows", pageData.stockRows());
            request.setAttribute("transactionRows", pageData.transactionRows());
            request.setAttribute("inventoryAvailable", Boolean.TRUE);
            List<Map<String, Object>> chartRows = prioritizedStockRows(pageData.stockRows());
            request.setAttribute("inventoryChartJson", AnalyticsJson.serialize(chartRows));
            request.setAttribute("inventoryChartIncludedCount", chartRows.size());
            request.setAttribute("inventoryChartTotalCount", pageData.stockRows().size());
            request.setAttribute("inventoryAnalyticsAvailable", Boolean.TRUE);
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Inventory page is unavailable because database settings are missing.");
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            logSqlDiagnostics(exception);
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    private Long parseIdFilter(String value, String label, HttpServletRequest request) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 19) {
            appendFilterError(request, "Choose a valid " + label + " filter.");
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                appendFilterError(request, "Choose a valid " + label + " filter.");
                return null;
            }
            return id;
        } catch (NumberFormatException exception) {
            appendFilterError(request, "Choose a valid " + label + " filter.");
            return null;
        }
    }

    private String parseTransactionType(String value, HttpServletRequest request) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!TRANSACTION_TYPES.contains(normalized)) {
            appendFilterError(request, "Choose a valid transaction type filter.");
            return null;
        }
        return normalized;
    }

    private void appendFilterError(HttpServletRequest request, String message) {
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) request.getAttribute("filterErrors");
        if (errors == null) {
            errors = new ArrayList<>();
            request.setAttribute("filterErrors", errors);
        }
        errors.add(message);
    }

    private void setUnavailableState(HttpServletRequest request) {
        request.setAttribute("inventoryAvailable", Boolean.FALSE);
        request.setAttribute("inventoryError",
                "Inventory data is temporarily unavailable. Review safe diagnostics in the Tomcat console.");
        request.setAttribute("blocks", List.of());
        request.setAttribute("ingredients", List.of());
        request.setAttribute("stockRows", List.of());
        request.setAttribute("transactionRows", List.of());
        request.setAttribute("inventoryChartJson", "[]");
        request.setAttribute("inventoryAnalyticsAvailable", Boolean.FALSE);
        request.setAttribute("inventoryChartIncludedCount", 0);
        request.setAttribute("inventoryChartTotalCount", 0);
    }

    private List<Map<String, Object>> prioritizedStockRows(List<Map<String, Object>> stockRows) {
        List<Map<String, Object>> ordered = new ArrayList<>(stockRows);
        ordered.sort(Comparator
                .comparing((Map<String, Object> row) -> text(row.get("baseUnit")),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> !Boolean.TRUE.equals(row.get("lowStock")))
                .thenComparing((Map<String, Object> row) -> text(row.get("blockName")),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing((Map<String, Object> row) -> text(row.get("ingredientName")),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(row -> ((Number) row.get("blockId")).longValue())
                .thenComparingLong(row -> ((Number) row.get("ingredientId")).longValue()));
        List<Map<String, Object>> selected = new ArrayList<>();
        String previousUnit = null;
        int unitCount = 0;
        for (Map<String, Object> row : ordered) {
            String unit = text(row.get("baseUnit"));
            if (!unit.equals(previousUnit)) {
                previousUnit = unit;
                unitCount = 0;
            }
            if (unitCount < STOCK_CHART_LIMIT) {
                selected.add(row);
                unitCount++;
            }
        }
        return List.copyOf(selected);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private void logSqlDiagnostics(SQLException exception) {
        SQLException current = exception;
        int count = 0;
        while (current != null && count < 5) {
            LOGGER.log(Level.WARNING,
                    "Inventory database query failed. exceptionClass={0}, SQLState={1}, vendorCode={2}",
                    new Object[] { current.getClass().getName(),
                            current.getSQLState() == null ? "unavailable" : current.getSQLState(),
                            current.getErrorCode() });
            current = current.getNextException();
            count++;
        }
        if (count == 5 && current != null) {
            LOGGER.warning("Additional chained inventory SQL diagnostics were omitted.");
        }
    }
}
