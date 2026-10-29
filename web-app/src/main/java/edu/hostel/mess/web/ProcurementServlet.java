package edu.hostel.mess.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.analytics.AnalyticsJson;
import edu.hostel.mess.web.procurement.ProcurementRepository;
import edu.hostel.mess.web.procurement.ProcurementRepository.ActivityFilters;
import edu.hostel.mess.web.procurement.ProcurementRepository.PageData;
import edu.hostel.mess.web.procurement.ProcurementRepository.PurchaseOrderDraft;
import edu.hostel.mess.web.procurement.ProcurementRepository.PurchaseOrderItemDraft;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/procurement")
public class ProcurementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/procurement.jsp";
    private static final Logger LOGGER = Logger.getLogger(ProcurementServlet.class.getName());
    private final ProcurementRepository repository = new ProcurementRepository();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        initializeBlankForm(request);
        request.setAttribute("created", "1".equals(request.getParameter("created")));
        loadAndForward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Map<String, String> formData = readFormData(request);
        List<Map<String, String>> itemRows = readItemRows(request);
        request.setAttribute("formData", formData);
        request.setAttribute("itemRows", itemRows);

        try {
            PurchaseOrderDraft draft = parseDraft(formData, itemRows);
            repository.createPurchaseOrder(draft);
            response.sendRedirect(response.encodeRedirectURL(
                    request.getContextPath() + "/procurement?created=1"));
            return;
        } catch (FormValidationException exception) {
            request.setAttribute("formErrors", exception.messages());
        } catch (ProcurementRepository.InvalidReferenceException
                | ProcurementRepository.DuplicatePurchaseOrderException exception) {
            request.setAttribute("formErrors", List.of(exception.getMessage()));
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Purchase order could not be saved because database settings are unavailable.");
            request.setAttribute("formErrors",
                    List.of("The database is not available. Please try again after it is restored."));
        } catch (SQLException exception) {
            logSqlDiagnostics(exception);
            request.setAttribute("formErrors", List.of(
                    "The purchase order could not be saved. Check that its number is unique and the selected records are still active, then try again."));
        }

        loadAndForward(request, response);
    }

    private void loadAndForward(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String fromText = parameterOrEmpty(request, "fromDate").trim();
        String toText = parameterOrEmpty(request, "toDate").trim();
        LocalDate fromDate = parseAnalyticsDate(fromText, "start", request);
        LocalDate toDate = parseAnalyticsDate(toText, "end", request);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            appendFilterError(request,
                    "The start date must be on or before the end date. The chart date filters were ignored.");
            fromDate = null;
            toDate = null;
        }
        request.setAttribute("selectedActivityFromDate", fromText);
        request.setAttribute("selectedActivityToDate", toText);
        request.setAttribute("procurementChartJson", "[]");
        request.setAttribute("procurementAnalyticsAvailable", Boolean.FALSE);

        try (Connection connection = OracleConnectionFactory.openConnection()) {
            PageData pageData = repository.loadPage(connection);
            request.setAttribute("vendors", pageData.vendors());
            request.setAttribute("blocks", pageData.blocks());
            request.setAttribute("ingredients", pageData.ingredients());
            request.setAttribute("purchaseOrders", pageData.purchaseOrders());
            request.setAttribute("purchaseOrderCount", pageData.purchaseOrders().size());
            request.setAttribute("procurementAvailable", Boolean.TRUE);
            try {
                request.setAttribute("procurementChartJson", AnalyticsJson.serialize(
                        repository.loadActivitySummaries(connection, new ActivityFilters(fromDate, toDate))));
                request.setAttribute("procurementAnalyticsAvailable", Boolean.TRUE);
            } catch (SQLException analyticsException) {
                LOGGER.log(Level.WARNING,
                        "Procurement activity chart data is unavailable. exceptionClass={0}, SQLState={1}, vendorCode={2}",
                        new Object[] { analyticsException.getClass().getName(),
                                analyticsException.getSQLState() == null ? "unavailable" : analyticsException.getSQLState(),
                                analyticsException.getErrorCode() });
            }
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Procurement page is unavailable because database settings are missing.");
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            logSqlDiagnostics(exception);
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    private void setUnavailableState(HttpServletRequest request) {
        request.setAttribute("procurementAvailable", Boolean.FALSE);
        request.setAttribute("procurementError",
                "Procurement data is temporarily unavailable. Review safe diagnostics in the Tomcat console.");
        request.setAttribute("vendors", List.of());
        request.setAttribute("blocks", List.of());
        request.setAttribute("ingredients", List.of());
        request.setAttribute("purchaseOrders", List.of());
        request.setAttribute("procurementChartJson", "[]");
        request.setAttribute("procurementAnalyticsAvailable", Boolean.FALSE);
    }

    private LocalDate parseAnalyticsDate(String value, String label, HttpServletRequest request) {
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() != 10) {
            appendFilterError(request, "Enter a valid chart " + label + " date.");
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            appendFilterError(request, "Enter a valid chart " + label + " date.");
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

    private Map<String, String> readFormData(HttpServletRequest request) {
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("poNumber", parameterOrEmpty(request, "poNumber"));
        formData.put("vendorId", parameterOrEmpty(request, "vendorId"));
        formData.put("blockId", parameterOrEmpty(request, "blockId"));
        formData.put("orderedAt", parameterOrEmpty(request, "orderedAt"));
        formData.put("expectedBy", parameterOrEmpty(request, "expectedBy"));
        formData.put("approvedAmount", parameterOrEmpty(request, "approvedAmount"));
        return formData;
    }

    private List<Map<String, String>> readItemRows(HttpServletRequest request) {
        String[] ingredientIds = request.getParameterValues("ingredientId");
        String[] quantities = request.getParameterValues("orderedQty");
        String[] prices = request.getParameterValues("unitPrice");
        int rowCount = Math.max(length(ingredientIds), Math.max(length(quantities), length(prices)));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("ingredientId", valueAt(ingredientIds, index));
            row.put("orderedQty", valueAt(quantities, index));
            row.put("unitPrice", valueAt(prices, index));
            rows.add(row);
        }
        if (rows.isEmpty()) {
            rows.add(blankItemRow());
        }
        return rows;
    }

    private PurchaseOrderDraft parseDraft(Map<String, String> formData,
            List<Map<String, String>> itemRows) throws FormValidationException {
        List<String> errors = new ArrayList<>();
        String poNumber = formData.get("poNumber").trim();
        if (poNumber.isEmpty()) {
            errors.add("Enter a purchase-order number.");
        } else if (poNumber.codePointCount(0, poNumber.length()) > 30) {
            errors.add("The purchase-order number must be 30 characters or fewer.");
        }

        long vendorId = parseRequiredId(formData.get("vendorId"), "Select an active vendor.", errors);
        long blockId = parseRequiredId(formData.get("blockId"), "Select an active mess block.", errors);
        LocalDate orderedAt = parseDate(formData.get("orderedAt"), "Enter a valid order date.", true, errors);
        LocalDate expectedBy = parseDate(formData.get("expectedBy"), "Enter a valid expected-delivery date.", false, errors);
        if (orderedAt != null && expectedBy != null && expectedBy.isBefore(orderedAt)) {
            errors.add("The expected-delivery date cannot be before the order date.");
        }

        BigDecimal approvedAmount = parseDecimal(formData.get("approvedAmount"), 2, 12,
                "Enter an approved amount with at most two decimal places.", errors);
        if (approvedAmount != null && approvedAmount.signum() < 0) {
            errors.add("The approved amount cannot be negative.");
        }

        List<PurchaseOrderItemDraft> items = new ArrayList<>();
        Set<Long> ingredientIds = new HashSet<>();
        BigDecimal itemTotal = BigDecimal.ZERO;
        for (Map<String, String> row : itemRows) {
            String ingredientValue = row.get("ingredientId").trim();
            String quantityValue = row.get("orderedQty").trim();
            String priceValue = row.get("unitPrice").trim();
            if (ingredientValue.isEmpty() && quantityValue.isEmpty() && priceValue.isEmpty()) {
                continue;
            }

            if (ingredientValue.isEmpty() || quantityValue.isEmpty() || priceValue.isEmpty()) {
                errors.add("Complete the ingredient, quantity, and unit price for each item row.");
                continue;
            }

            long ingredientId = parseRequiredId(ingredientValue,
                    "Choose a valid existing ingredient.", errors);
            BigDecimal quantity = parseDecimal(quantityValue, 3, 12,
                    "Enter a quantity with at most three decimal places.", errors);
            BigDecimal unitPrice = parseDecimal(priceValue, 2, 12,
                    "Enter a unit price with at most two decimal places.", errors);
            if (quantity != null && quantity.signum() <= 0) {
                errors.add("Each ordered quantity must be greater than zero.");
            }
            if (unitPrice != null && unitPrice.signum() < 0) {
                errors.add("Unit prices cannot be negative.");
            }

            if (ingredientId > 0 && !ingredientIds.add(ingredientId)) {
                errors.add("Select each ingredient only once. Combine its quantity into one line.");
            }
            if (ingredientId > 0 && quantity != null && quantity.signum() > 0
                    && unitPrice != null && unitPrice.signum() >= 0) {
                items.add(new PurchaseOrderItemDraft(ingredientId, quantity, unitPrice));
                itemTotal = itemTotal.add(quantity.multiply(unitPrice));
            }
        }

        if (items.isEmpty()) {
            errors.add("Add at least one complete purchase-order item.");
        }
        if (approvedAmount != null && itemTotal.compareTo(approvedAmount) > 0) {
            errors.add("The approved amount must cover the total value of the listed items.");
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        return new PurchaseOrderDraft(poNumber, vendorId, blockId, orderedAt,
                expectedBy, approvedAmount, List.copyOf(items));
    }

    private long parseRequiredId(String value, String errorMessage, List<String> errors) {
        try {
            long id = Long.parseLong(value.trim());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException exception) {
            // The form will receive the same safe validation message for malformed and missing IDs.
        }
        errors.add(errorMessage);
        return 0;
    }

    private BigDecimal parseDecimal(String value, int scale, int precision,
            String errorMessage, List<String> errors) {
        if (value == null || value.isBlank() || value.length() > 40) {
            errors.add(errorMessage);
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.trim()).setScale(scale, RoundingMode.UNNECESSARY);
            if (decimal.precision() > precision) {
                errors.add(errorMessage);
                return null;
            }
            return decimal;
        } catch (NumberFormatException | ArithmeticException exception) {
            errors.add(errorMessage);
            return null;
        }
    }

    private LocalDate parseDate(String value, String errorMessage, boolean required, List<String> errors) {
        if (value == null || value.isBlank()) {
            if (required) {
                errors.add(errorMessage);
            }
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            errors.add(errorMessage);
            return null;
        }
    }

    private void initializeBlankForm(HttpServletRequest request) {
        if (request.getAttribute("formData") != null) {
            return;
        }
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("poNumber", "");
        formData.put("vendorId", "");
        formData.put("blockId", "");
        formData.put("orderedAt", LocalDate.now().toString());
        formData.put("expectedBy", "");
        formData.put("approvedAmount", "");
        request.setAttribute("formData", formData);
        request.setAttribute("itemRows", List.of(blankItemRow()));
    }

    private Map<String, String> blankItemRow() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("ingredientId", "");
        row.put("orderedQty", "");
        row.put("unitPrice", "");
        return row;
    }

    private String parameterOrEmpty(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value;
    }

    private int length(String[] values) {
        return values == null ? 0 : values.length;
    }

    private String valueAt(String[] values, int index) {
        return values == null || index >= values.length || values[index] == null
                ? "" : values[index];
    }

    private void logSqlDiagnostics(SQLException exception) {
        SQLException current = exception;
        int count = 0;
        while (current != null && count < 5) {
            LOGGER.log(Level.WARNING,
                    "Procurement database operation failed. exceptionClass={0}, SQLState={1}, vendorCode={2}",
                    new Object[] { current.getClass().getName(),
                            current.getSQLState() == null ? "unavailable" : current.getSQLState(),
                            current.getErrorCode() });
            current = current.getNextException();
            count++;
        }
        if (current != null) {
            LOGGER.warning("Additional chained procurement SQL diagnostics were omitted.");
        }
    }

    private static final class FormValidationException extends Exception {
        private static final long serialVersionUID = 1L;
        private final List<String> messages;

        private FormValidationException(List<String> messages) {
            this.messages = List.copyOf(messages);
        }

        private List<String> messages() {
            return messages;
        }
    }
}

