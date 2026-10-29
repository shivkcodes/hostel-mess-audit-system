package edu.hostel.mess.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.db.SafeSqlDiagnostics;
import edu.hostel.mess.web.management.AdminDataRepository;
import edu.hostel.mess.web.management.AdminDataRepository.AdminException;
import edu.hostel.mess.web.management.AdminDataRepository.DailyDraft;
import edu.hostel.mess.web.security.AuthenticatedUser;
import edu.hostel.mess.web.security.SecurityFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Purpose-specific ADMIN screens for review and narrowly scoped data operations. */
@WebServlet(urlPatterns = { "/admin/data-management", "/admin/data-cleanup" })
public final class AdminDataServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AdminDataServlet.class.getName());
    private static final String MANAGEMENT_PATH = "/admin/data-management";
    private static final String CLEANUP_PATH = "/admin/data-cleanup";
    private static final String MANAGEMENT_JSP = "/WEB-INF/views/admin-data-management.jsp";
    private static final String CLEANUP_JSP = "/WEB-INF/views/admin-data-cleanup.jsp";
    private static final Set<String> MANAGEMENT_SECTIONS = Set.of("reference", "orders", "receipts",
            "daily", "accounts", "inventory", "ledger", "contracts", "audit");
    private static final Set<String> CLEANUP_SECTIONS = Set.of("orders", "receipts", "daily");
    private static final Set<String> SUCCESS_CODES = Set.of("daily-saved", "daily-deleted", "order-deleted",
            "order-cancelled", "receipt-deleted", "receipt-reversed", "user-updated");

    private final AdminDataRepository repository = new AdminDataRepository();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        render(request, response, null, HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String path = request.getServletPath();
        String action = bounded(request.getParameter("action"), 40);
        try {
            AuthenticatedUser actor = authenticatedUser(request);
            requireConfirmation(request);
            try (Connection connection = OracleConnectionFactory.openConnection()) {
                switch (action) {
                    case "delete-order" -> {
                        requirePath(path, CLEANUP_PATH);
                        repository.deleteUnreceivedOrder(connection, requiredId(request, "orderId"),
                                requestReason(request), actor.username());
                        redirect(response, request, CLEANUP_PATH, "orders", "order-deleted");
                    }
                    case "cancel-order" -> {
                        requirePath(path, CLEANUP_PATH);
                        repository.cancelOrder(connection, requiredId(request, "orderId"),
                                requestReason(request), actor.username());
                        redirect(response, request, CLEANUP_PATH, "orders", "order-cancelled");
                    }
                    case "reverse-receipt" -> {
                        requirePath(path, CLEANUP_PATH);
                        repository.reverseReceiptInventory(connection, requiredId(request, "receiptId"),
                                requestReason(request), actor.username());
                        redirect(response, request, CLEANUP_PATH, "receipts", "receipt-reversed");
                    }
                    case "delete-receipt" -> {
                        requirePath(path, CLEANUP_PATH);
                        repository.deleteUnprocessedReceipt(connection, requiredId(request, "receiptId"),
                                requestReason(request), actor.username());
                        redirect(response, request, CLEANUP_PATH, "receipts", "receipt-deleted");
                    }
                    case "save-daily" -> {
                        requirePath(path, MANAGEMENT_PATH);
                        Long id = strictOptionalId(request.getParameter("recordId"));
                        repository.saveDailyRecord(connection, id, dailyDraft(request), actor.username());
                        redirect(response, request, MANAGEMENT_PATH, "daily", "daily-saved");
                    }
                    case "delete-daily" -> {
                        requirePath(path, CLEANUP_PATH);
                        repository.deleteDailyRecord(connection, requiredId(request, "recordId"), actor.username());
                        redirect(response, request, CLEANUP_PATH, "daily", "daily-deleted");
                    }
                    case "update-user" -> {
                        requirePath(path, MANAGEMENT_PATH);
                        repository.updateUser(connection, requiredId(request, "userId"),
                                bounded(request.getParameter("role"), 20),
                                bounded(request.getParameter("active"), 1),
                                actor.userId(), actor.username());
                        redirect(response, request, MANAGEMENT_PATH, "accounts", "user-updated");
                    }
                    default -> {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Choose a supported admin operation.");
                    }
                }
            }
        } catch (AdminException | InvalidRequestException exception) {
            request.setAttribute("adminError", exception.getMessage());
            render(request, response, exception.getMessage(), HttpServletResponse.SC_CONFLICT);
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Admin data action unavailable because required database settings are missing.");
            render(request, response, "Administrative data is temporarily unavailable.",
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            SafeSqlDiagnostics.log(LOGGER, "AdminDataServlet", "admin data operation", exception);
            render(request, response, safeDatabaseMessage(exception), HttpServletResponse.SC_CONFLICT);
        }
    }

    private void render(HttpServletRequest request, HttpServletResponse response, String error, int status)
            throws ServletException, IOException {
        boolean cleanup = CLEANUP_PATH.equals(request.getServletPath());
        String jsp = cleanup ? CLEANUP_JSP : MANAGEMENT_JSP;
        String section = selectedSection(request.getParameter("section"), cleanup);
        String search = bounded(request.getParameter("q"), 80);
        request.setAttribute("adminPage", cleanup ? "cleanup" : "management");
        request.setAttribute("adminSection", section);
        request.setAttribute("adminSectionLabel", sectionLabel(section));
        request.setAttribute("adminSearchPlaceholder", searchPlaceholder(section));
        request.setAttribute("adminDataAvailable", Boolean.TRUE);
        request.setAttribute("adminSearch", search);
        request.setAttribute("successMessage", successMessage(request.getParameter("saved")));
        if (error != null) request.setAttribute("adminError", error);

        if (!"reference".equals(section)) {
            try (Connection connection = OracleConnectionFactory.openConnection()) {
                switch (section) {
                    case "orders" -> {
                        request.setAttribute("adminOrders", repository.loadOrders(connection, search));
                        Long orderId = optionalId(request.getParameter("orderId"));
                        if (orderId != null) {
                            Map<String, Object> order = repository.loadOrder(connection, orderId);
                            request.setAttribute("selectedOrder", order);
                            if (order != null) request.setAttribute("selectedOrderItems", repository.loadOrderItems(connection, orderId));
                        }
                    }
                    case "receipts" -> {
                        request.setAttribute("adminReceipts", repository.loadReceipts(connection, search));
                        Long receiptId = optionalId(request.getParameter("receiptId"));
                        if (receiptId != null) {
                            Map<String, Object> receipt = repository.loadReceipt(connection, receiptId);
                            request.setAttribute("selectedReceipt", receipt);
                            if (receipt != null) request.setAttribute("selectedReceiptItems", repository.loadReceiptItems(connection, receiptId));
                        }
                    }
                    case "daily" -> {
                        request.setAttribute("adminDailyRecords", repository.loadDailyRecords(connection, search));
                        request.setAttribute("adminBlocks", repository.loadBlocks(connection));
                        Long recordId = optionalId(request.getParameter("recordId"));
                        if (recordId != null) request.setAttribute("selectedDailyRecord", repository.loadDailyRecord(connection, recordId));
                    }
                    case "accounts" -> {
                        request.setAttribute("adminUsers", repository.loadUsers(connection, search));
                        Long userId = optionalId(request.getParameter("userId"));
                        if (userId != null) request.setAttribute("selectedAdminUser", repository.loadUserDetails(connection, userId));
                    }
                    case "inventory" -> request.setAttribute("adminInventory", repository.loadInventory(connection));
                    case "ledger" -> request.setAttribute("adminTransactions", repository.loadTransactions(connection));
                    case "contracts" -> {
                        request.setAttribute("adminContractRules", repository.loadContractRules(connection));
                        request.setAttribute("adminPenaltyReviews", repository.loadPenaltyReviews(connection));
                    }
                    case "audit" -> request.setAttribute("adminAuditEvents", repository.loadAuditEvents(connection));
                    default -> throw new IllegalStateException("Unsupported admin section.");
                }
            } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
                LOGGER.warning("Admin data page unavailable because required database settings are missing.");
                request.setAttribute("adminError", "Administrative data is temporarily unavailable.");
                request.setAttribute("adminDataAvailable", Boolean.FALSE);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            } catch (SQLException exception) {
                SafeSqlDiagnostics.log(LOGGER, "AdminDataServlet", "admin data page", exception);
                request.setAttribute("adminError", "Administrative data is temporarily unavailable.");
                request.setAttribute("adminDataAvailable", Boolean.FALSE);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        }
        if (response.getStatus() < 400 && status >= 400) response.setStatus(status);
        request.getRequestDispatcher(jsp).forward(request, response);
    }

    static String selectedSection(String requested, boolean cleanup) {
        Set<String> supported = cleanup ? CLEANUP_SECTIONS : MANAGEMENT_SECTIONS;
        if (requested != null && supported.contains(requested)) return requested;
        return cleanup ? "orders" : "reference";
    }

    private String sectionLabel(String section) {
        return switch (section) {
            case "reference" -> "Reference Data";
            case "orders" -> "Purchase Orders";
            case "receipts" -> "Goods Receipts";
            case "daily" -> "Daily Mess Records";
            case "accounts" -> "User Accounts";
            case "inventory" -> "Inventory Balances";
            case "ledger" -> "Inventory Transaction Ledger";
            case "contracts" -> "Contract Rules and Penalty Reviews";
            case "audit" -> "Audit History";
            default -> "Administrative Records";
        };
    }

    private String searchPlaceholder(String section) {
        return switch (section) {
            case "reference" -> "Search vendors, ingredients, or blocks";
            case "orders" -> "Search order number, vendor, status, or block";
            case "receipts" -> "Search receipt, order, vendor, or invoice reference";
            case "daily" -> "Search date, block, or measurement details";
            case "accounts" -> "Search username or role";
            case "inventory" -> "Search ingredient or mess block";
            case "ledger" -> "Search reference, ingredient, transaction, or notes";
            case "contracts" -> "Search vendor, rule, review, or status";
            case "audit" -> "Search entity, record key, operation, or actor";
            default -> "Search this category";
        };
    }

    private DailyDraft dailyDraft(HttpServletRequest request) throws InvalidRequestException {
        long blockId = parsePositiveId(request.getParameter("blockId"), "Choose a valid mess block.");
        LocalDate date;
        try {
            String raw = strictText(request.getParameter("recordDate"), 10, "Enter a valid record date.");
            date = LocalDate.parse(raw);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new InvalidRequestException("Enter a valid record date.");
        }
        int meals;
        try {
            String raw = strictText(request.getParameter("mealsServed"), 8, "Enter a whole number of meals served.");
            meals = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new InvalidRequestException("Enter a whole number of meals served.");
        }
        return new DailyDraft(blockId, date, meals,
                decimalOrNull(request.getParameter("preparedKg"), "Prepared quantity"),
                decimalOrNull(request.getParameter("leftoverKg"), "Measured leftovers"),
                nullableBounded(request.getParameter("measurementMethod"), 200),
                nullableBounded(request.getParameter("notes"), 500));
    }

    private BigDecimal decimalOrNull(String raw, String label) throws InvalidRequestException {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        if (value.length() > 30 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException(label + " is outside the supported range.");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new InvalidRequestException(label + " must be a valid decimal quantity.");
        }
    }

    private void requireConfirmation(HttpServletRequest request) throws InvalidRequestException {
        String action = request.getParameter("action");
        if (action != null && Set.of("delete-order", "delete-receipt", "cancel-order", "reverse-receipt", "delete-daily", "update-user").contains(action)
                && !"yes".equals(request.getParameter("confirm"))) {
            throw new InvalidRequestException("Confirm this specific action before submitting it.");
        }
    }

    private String requestReason(HttpServletRequest request) throws InvalidRequestException {
        String reason = request.getParameter("reason");
        if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > 400
                || reason.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException("Enter a reason of 1 to 400 characters.");
        }
        return reason.trim();
    }

    private void requirePath(String actual, String expected) throws InvalidRequestException {
        if (!expected.equals(actual)) throw new InvalidRequestException("That operation is not available on this page.");
    }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request) throws InvalidRequestException {
        Object value = request.getAttribute("authenticatedUser");
        if (value instanceof AuthenticatedUser user) return user;
        throw new InvalidRequestException("Sign in with an administrator account to continue.");
    }

    private long requiredId(HttpServletRequest request, String field) throws InvalidRequestException {
        return parsePositiveId(request.getParameter(field), "Choose a valid record.");
    }

    private long parsePositiveId(String value, String message) throws InvalidRequestException {
        Long id = optionalId(value);
        if (id == null) throw new InvalidRequestException(message);
        return id;
    }

    private Long optionalId(String value) {
        if (value == null || value.isBlank() || value.length() > 19) return null;
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long strictOptionalId(String value) throws InvalidRequestException {
        if (value == null || value.isBlank()) return null;
        Long id = optionalId(value);
        if (id == null) throw new InvalidRequestException("Choose a valid record ID.");
        return id;
    }

    private String bounded(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String nullableBounded(String value, int maxLength) throws InvalidRequestException {
        if (value == null || value.isBlank()) return null;
        if (value.codePointCount(0, value.length()) > maxLength
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException("A text field exceeds the allowed length or contains control characters.");
        }
        return value.trim();
    }

    private String strictText(String value, int maxLength, String message) throws InvalidRequestException {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.codePoints().anyMatch(Character::isISOControl)) throw new InvalidRequestException(message);
        return value.trim();
    }

    private String safeDatabaseMessage(SQLException exception) {
        if (exception.getErrorCode() == 1) return "A record with those unique details already exists.";
        if (exception.getErrorCode() == 2292) return "This record is referenced by operational history and cannot be removed.";
        if (exception.getErrorCode() == 2290) return "The submitted values do not satisfy a database rule. Review the form and try again.";
        return "The administrative operation could not be completed. No success was recorded.";
    }

    String successMessage(String code) {
        if (code == null || !SUCCESS_CODES.contains(code)) return null;
        return switch (code) {
            case "daily-saved" -> "Daily mess record saved.";
            case "daily-deleted" -> "Daily mess record deleted and recorded in the audit log.";
            case "order-deleted" -> "Unreceived purchase order and its unreferenced items deleted.";
            case "order-cancelled" -> "Purchase order cancelled; its history remains available.";
            case "receipt-deleted" -> "Unverified receipt and its unreferenced lines deleted; audit evidence was recorded.";
            case "receipt-reversed" -> "Compensating inventory transactions were recorded; the original receipt remains.";
            case "user-updated" -> "Account permissions and active status updated.";
            default -> null;
        };
    }

    private void redirect(HttpServletResponse response, HttpServletRequest request, String path,
            String section, String message)
            throws IOException {
        response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + path
                + "?section=" + section + "&saved=" + message));
    }

    private static final class InvalidRequestException extends Exception {
        private static final long serialVersionUID = 1L;
        InvalidRequestException(String message) { super(message); }
    }
}
