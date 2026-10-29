package edu.hostel.mess.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.analytics.AnalyticsJson;
import edu.hostel.mess.web.wastage.FoodWastageRepository;
import edu.hostel.mess.web.wastage.FoodWastageRepository.Filters;
import edu.hostel.mess.web.wastage.FoodWastageRepository.PageData;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/food-wastage")
public class FoodWastageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/food-wastage.jsp";
    private static final Logger LOGGER = Logger.getLogger(FoodWastageServlet.class.getName());
    private static final int CHART_DATE_LIMIT = 90;
    private final FoodWastageRepository repository = new FoodWastageRepository();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String selectedFromDate = parameterOrEmpty(request, "fromDate").trim();
        String selectedToDate = parameterOrEmpty(request, "toDate").trim();
        LocalDate fromDate = parseDateFilter(selectedFromDate, "start", request);
        LocalDate toDate = parseDateFilter(selectedToDate, "end", request);
        Long blockId = parseBlockFilter(request.getParameter("blockId"), request);

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            appendFilterError(request, "The start date must be on or before the end date. The date filters were ignored.");
            fromDate = null;
            toDate = null;
        }

        request.setAttribute("selectedFromDate", selectedFromDate);
        request.setAttribute("selectedToDate", selectedToDate);
        request.setAttribute("selectedBlockId", blockId == null ? "" : blockId.toString());
        request.setAttribute("wastageChartJson", "[]");
        request.setAttribute("wastageChartDateCount", 0);

        try (Connection connection = OracleConnectionFactory.openConnection()) {
            PageData pageData = repository.loadPage(connection, new Filters(fromDate, toDate, blockId));
            request.setAttribute("blocks", pageData.blocks());
            request.setAttribute("summary", pageData.summary());
            request.setAttribute("blockSummaries", pageData.blockSummaries());
            request.setAttribute("dateSummaries", pageData.dateSummaries());
            request.setAttribute("dailyRecords", pageData.dailyRecords());
            List<Map<String, Object>> chartRows = chartDateRows(pageData.dateSummaries());
            request.setAttribute("wastageChartJson", AnalyticsJson.serialize(chartRows));
            request.setAttribute("wastageChartDateCount", chartRows.size());
            request.setAttribute("foodWastageAvailable", Boolean.TRUE);
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Food wastage page is unavailable because database settings are missing.");
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            logSqlDiagnostics(exception);
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    private LocalDate parseDateFilter(String value, String label, HttpServletRequest request) {
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() != 10) {
            appendFilterError(request, "Enter a valid " + label + " date.");
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            appendFilterError(request, "Enter a valid " + label + " date.");
            return null;
        }
    }

    private Long parseBlockFilter(String value, HttpServletRequest request) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 19) {
            appendFilterError(request, "Choose a valid mess block filter.");
            return null;
        }
        try {
            long blockId = Long.parseLong(value.trim());
            if (blockId <= 0) {
                appendFilterError(request, "Choose a valid mess block filter.");
                return null;
            }
            return blockId;
        } catch (NumberFormatException exception) {
            appendFilterError(request, "Choose a valid mess block filter.");
            return null;
        }
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
        request.setAttribute("foodWastageAvailable", Boolean.FALSE);
        request.setAttribute("foodWastageError",
                "Food wastage and consumption data is temporarily unavailable. Review safe diagnostics in the Tomcat console.");
        request.setAttribute("blocks", List.of());
        request.setAttribute("summary", null);
        request.setAttribute("blockSummaries", List.of());
        request.setAttribute("dateSummaries", List.of());
        request.setAttribute("dailyRecords", List.of());
        request.setAttribute("wastageChartJson", "[]");
        request.setAttribute("wastageChartDateCount", 0);
    }

    /** Retains the most recent actual dates from the repository's descending summary result. */
    private List<Map<String, Object>> chartDateRows(List<Map<String, Object>> dateSummaries) {
        int count = Math.min(CHART_DATE_LIMIT, dateSummaries.size());
        List<Map<String, Object>> rows = new ArrayList<>(count);
        for (int index = count - 1; index >= 0; index--) {
            Map<String, Object> summary = dateSummaries.get(index);
            Object reportDate = summary.get("reportDate");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reportDate", reportDate == null ? null : reportDate.toString());
            row.put("preparedKg", summary.get("preparedKg"));
            row.put("preparedRecordCount", summary.get("preparedRecordCount"));
            row.put("measuredLeftoverKg", summary.get("measuredLeftoverKg"));
            row.put("measuredRecordCount", summary.get("measuredRecordCount"));
            row.put("mealsServed", summary.get("mealsServed"));
            row.put("weightedPerMealKg", summary.get("weightedPerMealKg"));
            row.put("perMealRecordCount", summary.get("perMealRecordCount"));
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private String parameterOrEmpty(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value;
    }

    private void logSqlDiagnostics(SQLException exception) {
        SQLException current = exception;
        int count = 0;
        while (current != null && count < 5) {
            LOGGER.log(Level.WARNING,
                    "Food wastage database query failed. exceptionClass={0}, SQLState={1}, vendorCode={2}",
                    new Object[] { current.getClass().getName(),
                            current.getSQLState() == null ? "unavailable" : current.getSQLState(),
                            current.getErrorCode() });
            current = current.getNextException();
            count++;
        }
        if (count == 5 && current != null) {
            LOGGER.warning("Additional chained food wastage SQL diagnostics were omitted.");
        }
    }


}
