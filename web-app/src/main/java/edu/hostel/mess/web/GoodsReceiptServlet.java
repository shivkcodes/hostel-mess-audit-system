package edu.hostel.mess.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import edu.hostel.mess.web.receipt.GoodsReceiptRepository;
import edu.hostel.mess.web.receipt.GoodsReceiptRepository.IneligiblePurchaseOrderException;
import edu.hostel.mess.web.receipt.GoodsReceiptRepository.InvalidReceiptException;
import edu.hostel.mess.web.receipt.GoodsReceiptRepository.PageData;
import edu.hostel.mess.web.receipt.GoodsReceiptRepository.ReceiptDraft;
import edu.hostel.mess.web.receipt.GoodsReceiptRepository.ReceiptLineDraft;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/goods-receipts")
public class GoodsReceiptServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/goods-receipts.jsp";
    private static final Logger LOGGER = Logger.getLogger(GoodsReceiptServlet.class.getName());
    private static final DateTimeFormatter FORM_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private final GoodsReceiptRepository repository = new GoodsReceiptRepository();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        initializeBlankForm(request);
        request.setAttribute("created", "1".equals(request.getParameter("created")));
        Long selectedPoId = parsePageOrderId(request.getParameter("poId"), request);
        loadAndForward(request, response, selectedPoId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Map<String, String> formData = readFormData(request);
        List<Map<String, String>> lineRows = readLineRows(request);
        request.setAttribute("receiptData", formData);
        request.setAttribute("submittedLineRows", lineRows);

        Long selectedPoId = parsePositiveLong(formData.get("poId"));
        try {
            ReceiptDraft draft = parseDraft(formData, lineRows);
            repository.createReceipt(draft);
            response.sendRedirect(response.encodeRedirectURL(
                    request.getContextPath() + "/goods-receipts?created=1"));
            return;
        } catch (FormValidationException exception) {
            request.setAttribute("formErrors", exception.messages());
        } catch (IneligiblePurchaseOrderException | InvalidReceiptException exception) {
            request.setAttribute("formErrors", List.of(exception.getMessage()));
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Goods receipt could not be saved because database settings are unavailable.");
            request.setAttribute("formErrors",
                    List.of("The database is not available. Please try again after it is restored."));
        } catch (SQLException exception) {
            logSqlDiagnostics(exception);
            request.setAttribute("formErrors", List.of(
                    "The receipt could not be saved. Check that the invoice reference is unique and the order still has quantity due, then try again."));
        }

        loadAndForward(request, response, selectedPoId);
    }

    private Long parsePageOrderId(String value, HttpServletRequest request) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Long parsed = parsePositiveLong(value);
        if (parsed == null) {
            request.setAttribute("formErrors", List.of("Choose an open purchase order from the list."));
        }
        return parsed;
    }

    private void loadAndForward(HttpServletRequest request, HttpServletResponse response, Long selectedPoId)
            throws ServletException, IOException {
        try (Connection connection = OracleConnectionFactory.openConnection()) {
            PageData pageData;
            try {
                pageData = repository.loadPage(connection, selectedPoId);
            } catch (IneligiblePurchaseOrderException exception) {
                appendFormError(request, exception.getMessage());
                pageData = repository.loadPage(connection);
            }
            request.setAttribute("eligibleOrders", pageData.eligibleOrders());
            request.setAttribute("selectedOrder", pageData.selectedOrder());
            request.setAttribute("receiptItems", mergeSubmittedValues(
                    pageData.outstandingItems(), submittedLineRows(request)));
            request.setAttribute("receiptAvailable", Boolean.TRUE);
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Goods receipt page is unavailable because database settings are missing.");
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            logSqlDiagnostics(exception);
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    private List<Map<String, Object>> mergeSubmittedValues(List<Map<String, Object>> databaseRows,
            List<Map<String, String>> submittedRows) {
        Map<String, Map<String, String>> submittedByItem = new LinkedHashMap<>();
        for (Map<String, String> row : submittedRows) {
            submittedByItem.put(row.get("poItemId"), row);
        }

        List<Map<String, Object>> displayRows = new ArrayList<>();
        for (Map<String, Object> databaseRow : databaseRows) {
            Map<String, Object> displayRow = new LinkedHashMap<>(databaseRow);
            Map<String, String> submittedRow = submittedByItem.get(
                    String.valueOf(databaseRow.get("poItemId")));
            if (submittedRow != null) {
                displayRow.put("receivedQty", submittedRow.get("receivedQty"));
                displayRow.put("acceptedQty", submittedRow.get("acceptedQty"));
                displayRow.put("rejectedQty", submittedRow.get("rejectedQty"));
            }
            displayRows.add(displayRow);
        }
        return displayRows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> submittedLineRows(HttpServletRequest request) {
        Object value = request.getAttribute("submittedLineRows");
        return value instanceof List<?> ? (List<Map<String, String>>) value : List.of();
    }

    private Map<String, String> readFormData(HttpServletRequest request) {
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("poId", parameterOrEmpty(request, "poId"));
        formData.put("invoiceReference", parameterOrEmpty(request, "invoiceReference"));
        formData.put("invoiceAmount", parameterOrEmpty(request, "invoiceAmount"));
        formData.put("arrivedAt", parameterOrEmpty(request, "arrivedAt"));
        formData.put("verifiedBy", parameterOrEmpty(request, "verifiedBy"));
        formData.put("notes", parameterOrEmpty(request, "notes"));
        return formData;
    }

    private List<Map<String, String>> readLineRows(HttpServletRequest request) {
        String[] itemIds = request.getParameterValues("poItemId");
        String[] receivedValues = request.getParameterValues("receivedQty");
        String[] acceptedValues = request.getParameterValues("acceptedQty");
        String[] rejectedValues = request.getParameterValues("rejectedQty");
        int rowCount = Math.max(length(itemIds), Math.max(length(receivedValues),
                Math.max(length(acceptedValues), length(rejectedValues))));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("poItemId", valueAt(itemIds, index));
            row.put("receivedQty", valueAt(receivedValues, index));
            row.put("acceptedQty", valueAt(acceptedValues, index));
            row.put("rejectedQty", valueAt(rejectedValues, index));
            rows.add(row);
        }
        return rows;
    }

    private ReceiptDraft parseDraft(Map<String, String> formData, List<Map<String, String>> lineRows)
            throws FormValidationException {
        List<String> errors = new ArrayList<>();
        long poId = parseRequiredId(formData.get("poId"), "Choose an open purchase order.", errors);

        String invoiceReference = formData.get("invoiceReference").trim();
        if (invoiceReference.isEmpty()) {
            errors.add("Enter the invoice reference.");
        } else if (invoiceReference.codePointCount(0, invoiceReference.length()) > 60) {
            errors.add("The invoice reference must be 60 characters or fewer.");
        }

        BigDecimal invoiceAmount = parseDecimal(formData.get("invoiceAmount"), 2, 12,
                "Enter an invoice amount with at most two decimal places.", errors);
        if (invoiceAmount != null && invoiceAmount.signum() < 0) {
            errors.add("The invoice amount cannot be negative.");
        }

        LocalDateTime arrivedAt = parseDateTime(formData.get("arrivedAt"), errors);
        String verifiedBy = formData.get("verifiedBy").trim();
        if (verifiedBy.isEmpty()) {
            errors.add("Enter the name of the person verifying this delivery.");
        } else if (verifiedBy.codePointCount(0, verifiedBy.length()) > 100) {
            errors.add("The verifier name must be 100 characters or fewer.");
        }
        String notes = formData.get("notes").trim();
        if (notes.codePointCount(0, notes.length()) > 500) {
            errors.add("Receipt notes must be 500 characters or fewer.");
        }

        List<ReceiptLineDraft> lines = new ArrayList<>();
        Set<Long> itemIds = new HashSet<>();
        boolean hasPositiveReceivedQty = false;
        for (Map<String, String> row : lineRows) {
            String itemIdValue = row.get("poItemId").trim();
            String receivedValue = row.get("receivedQty").trim();
            String acceptedValue = row.get("acceptedQty").trim();
            String rejectedValue = row.get("rejectedQty").trim();
            if (itemIdValue.isEmpty() && receivedValue.isEmpty()
                    && acceptedValue.isEmpty() && rejectedValue.isEmpty()) {
                continue;
            }

            long poItemId = parseRequiredId(itemIdValue,
                    "Refresh the page and select a valid purchase-order item.", errors);
            BigDecimal receivedQty = parseDecimal(receivedValue, 3, 12,
                    "Enter received quantities with at most three decimal places.", errors);
            BigDecimal acceptedQty = parseDecimal(acceptedValue, 3, 12,
                    "Enter accepted quantities with at most three decimal places.", errors);
            BigDecimal rejectedQty = parseDecimal(rejectedValue, 3, 12,
                    "Enter rejected quantities with at most three decimal places.", errors);
            if (poItemId > 0 && !itemIds.add(poItemId)) {
                errors.add("Each purchase-order item may appear only once in this receipt.");
            }
            if (receivedQty != null && acceptedQty != null && rejectedQty != null) {
                if (receivedQty.signum() < 0 || acceptedQty.signum() < 0 || rejectedQty.signum() < 0) {
                    errors.add("Receipt quantities cannot be negative.");
                }
                if (acceptedQty.add(rejectedQty).compareTo(receivedQty) > 0) {
                    errors.add("Accepted plus rejected quantity cannot exceed received quantity.");
                } else if (acceptedQty.add(rejectedQty).compareTo(receivedQty) < 0) {
                    errors.add("Accepted and rejected quantities must add up to received quantity under the database constraint.");
                }
                hasPositiveReceivedQty |= receivedQty.signum() > 0;
                if (poItemId > 0) {
                    lines.add(new ReceiptLineDraft(poItemId, receivedQty, acceptedQty, rejectedQty, null));
                }
            }
        }
        if (!hasPositiveReceivedQty) {
            errors.add("Enter a positive received quantity for at least one item.");
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
        return new ReceiptDraft(poId, invoiceReference, invoiceAmount, arrivedAt,
                verifiedBy, notes, List.copyOf(lines));
    }

    private long parseRequiredId(String value, String message, List<String> errors) {
        Long id = parsePositiveLong(value);
        if (id == null) {
            errors.add(message);
            return 0;
        }
        return id;
    }

    private Long parsePositiveLong(String value) {
        if (value == null || value.isBlank() || value.length() > 19) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
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

    private LocalDateTime parseDateTime(String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add("Enter the date and time the delivery arrived.");
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            errors.add("Enter a valid delivery arrival date and time.");
            return null;
        }
    }

    private void initializeBlankForm(HttpServletRequest request) {
        if (request.getAttribute("receiptData") != null) {
            return;
        }
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("poId", "");
        formData.put("invoiceReference", "");
        formData.put("invoiceAmount", "");
        formData.put("arrivedAt", LocalDateTime.now().withSecond(0).withNano(0).format(FORM_DATE_TIME));
        formData.put("verifiedBy", "");
        formData.put("notes", "");
        request.setAttribute("receiptData", formData);
    }

    private void setUnavailableState(HttpServletRequest request) {
        request.setAttribute("receiptAvailable", Boolean.FALSE);
        request.setAttribute("receiptError",
                "Goods receipt data is temporarily unavailable. Review safe diagnostics in the Tomcat console.");
        request.setAttribute("eligibleOrders", List.of());
        request.setAttribute("receiptItems", List.of());
        request.setAttribute("selectedOrder", null);
    }

    private void appendFormError(HttpServletRequest request, String message) {
        Object existing = request.getAttribute("formErrors");
        List<String> errors = new ArrayList<>();
        if (existing instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof String text) {
                    errors.add(text);
                }
            }
        }
        errors.add(message);
        request.setAttribute("formErrors", errors);
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
                    "Goods receipt database operation failed. exceptionClass={0}, SQLState={1}, vendorCode={2}",
                    new Object[] { current.getClass().getName(),
                            current.getSQLState() == null ? "unavailable" : current.getSQLState(),
                            current.getErrorCode() });
            current = current.getNextException();
            count++;
        }
        if (current != null) {
            LOGGER.warning("Additional chained goods receipt SQL diagnostics were omitted.");
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
