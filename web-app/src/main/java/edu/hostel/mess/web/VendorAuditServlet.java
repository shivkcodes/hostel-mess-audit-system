package edu.hostel.mess.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import edu.hostel.mess.web.audit.VendorAuditRepository;
import edu.hostel.mess.web.audit.VendorAuditRepository.Filters;
import edu.hostel.mess.web.audit.VendorAuditRepository.PageData;
import edu.hostel.mess.web.analytics.AnalyticsJson;
import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.db.SafeSqlDiagnostics;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/vendor-audit")
public class VendorAuditServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/vendor-audit.jsp";
    private static final Logger LOGGER = Logger.getLogger(VendorAuditServlet.class.getName());
    private final VendorAuditRepository repository = new VendorAuditRepository();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String fromText = parameterOrEmpty(request, "fromDate").trim();
        String toText = parameterOrEmpty(request, "toDate").trim();
        String vendorText = parameterOrEmpty(request, "vendorId").trim();
        LocalDate fromDate = parseDate(fromText, "start", request);
        LocalDate toDate = parseDate(toText, "end", request);
        Long vendorId = parseVendorId(vendorText, request);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            addFilterError(request, "The start date must be on or before the end date. Date filters were ignored.");
            fromDate = null;
            toDate = null;
        }

        request.setAttribute("selectedFromDate", fromText);
        request.setAttribute("selectedToDate", toText);
        request.setAttribute("selectedVendorId", vendorText);
        request.setAttribute("vendorChartJson", "[]");
        String phase = "connection establishment";
        try (Connection connection = OracleConnectionFactory.openConnection()) {
            phase = "vendor audit SQL execution and result retrieval";
            PageData page = repository.loadPage(connection, new Filters(fromDate, toDate, vendorId));
            if (vendorId != null && page.selectedVendor() == null) {
                addFilterError(request, "No vendor matches the selected vendor filter.");
            }
            request.setAttribute("vendors", page.vendors());
            request.setAttribute("summary", page.summary());
            request.setAttribute("vendorSummaries", page.vendorSummaries());
            request.setAttribute("quantitySummaries", page.quantitySummaries());
            request.setAttribute("selectedVendor", page.selectedVendor());
            request.setAttribute("orders", page.orders());
            request.setAttribute("orderLines", page.orderLines());
            request.setAttribute("receiptEvidence", page.receiptEvidence());
            request.setAttribute("vendorChartJson", AnalyticsJson.serialize(page.quantitySummaries()));
            request.setAttribute("vendorAuditAvailable", Boolean.TRUE);
            phase = "connection cleanup";
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Vendor audit page is unavailable because database settings are missing.");
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            logSqlDiagnostics(phase, exception);
            setUnavailableState(request);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    private LocalDate parseDate(String value, String label, HttpServletRequest request) {
        if (value.isEmpty()) return null;
        if (value.length() != 10) {
            addFilterError(request, "Enter a valid " + label + " date.");
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            addFilterError(request, "Enter a valid " + label + " date.");
            return null;
        }
    }

    private Long parseVendorId(String value, HttpServletRequest request) {
        if (value.isEmpty()) return null;
        if (value.length() > 19) {
            addFilterError(request, "Choose a valid vendor filter.");
            return null;
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            addFilterError(request, "Choose a valid vendor filter.");
            return null;
        }
    }

    private void addFilterError(HttpServletRequest request, String message) {
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) request.getAttribute("filterErrors");
        if (errors == null) {
            errors = new ArrayList<>();
            request.setAttribute("filterErrors", errors);
        }
        errors.add(message);
    }

    private String parameterOrEmpty(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value;
    }

    private void setUnavailableState(HttpServletRequest request) {
        request.setAttribute("vendorAuditAvailable", Boolean.FALSE);
        request.setAttribute("vendorAuditError",
                "Vendor audit data is temporarily unavailable. Review safe diagnostics in the Tomcat console.");
        request.setAttribute("vendors", List.of());
        request.setAttribute("summary", null);
        request.setAttribute("vendorSummaries", List.of());
        request.setAttribute("quantitySummaries", List.of());
        request.setAttribute("selectedVendor", null);
        request.setAttribute("orders", List.of());
        request.setAttribute("orderLines", List.of());
        request.setAttribute("receiptEvidence", List.of());
        request.setAttribute("vendorChartJson", "[]");
    }

    private void logSqlDiagnostics(String phase, SQLException exception) {
        SafeSqlDiagnostics.log(LOGGER, "VendorAuditServlet", phase, exception);
    }
}
