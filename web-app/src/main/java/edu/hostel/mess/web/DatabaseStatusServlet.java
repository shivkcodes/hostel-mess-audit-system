package edu.hostel.mess.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.db.SafeSqlDiagnostics;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = { "/database-status", "/database-status/check", "/database-status/indicator" })
public class DatabaseStatusServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String QUERY = "SELECT 1 FROM DUAL";
    private static final String JSP_PATH = "/WEB-INF/views/database-status.jsp";
    private static final Logger LOGGER = Logger.getLogger(DatabaseStatusServlet.class.getName());
    private static final DateTimeFormatter CHECKED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm:ss z", Locale.ENGLISH);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);
        HealthCheckResult result = runHealthCheck();
        boolean connected = result.connected();
        String checkedAtIso = Instant.now().toString();

        if ("/database-status/indicator".equals(request.getServletPath())) {
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.setStatus(connected ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("{\"status\":\"" + (connected ? "CONNECTED" : "UNAVAILABLE")
                    + "\",\"checkedAt\":\"" + checkedAtIso + "\"}");
            return;
        }

        request.setAttribute("connectionStatus", connected ? "CONNECTED" : "UNAVAILABLE");
        request.setAttribute("healthStatusCode", connected ? "CONNECTED" : "UNAVAILABLE");
        request.setAttribute("healthCheckedAtIso", checkedAtIso);
        request.setAttribute("connected", connected);
        request.setAttribute("connectionTimeMs", result.connectionTimeMs());
        request.setAttribute("queryTimeMs", result.queryTimeMs());
        request.setAttribute("totalTimeMs", result.totalTimeMs());
        request.setAttribute("querySucceeded", result.querySucceeded());
        request.setAttribute("queryResult", result.querySucceeded() ? "SELECT_RESULT=1" : "");
        request.setAttribute("lastCheckedAt", ZonedDateTime.now().format(CHECKED_AT_FORMAT));
        request.setAttribute("statusMessage", connected
                ? "The database connection and health query both succeeded."
                : "The database health check could not be completed. Review the safe server diagnostics.");

        response.setStatus(connected ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    private HealthCheckResult runHealthCheck() {
        long totalStartNanos = System.nanoTime();
        Long connectionStartNanos = null;
        BigDecimal connectionTimeMs = null;
        BigDecimal queryTimeMs = null;
        BigDecimal totalTimeMs = BigDecimal.ZERO;
        boolean querySucceeded = false;
        boolean checkSucceeded = false;
        SQLException sqlFailure = null;
        String failurePhase = "connection establishment";

        try {
            connectionStartNanos = System.nanoTime();
            Connection openedConnection = OracleConnectionFactory.openConnection();
            connectionTimeMs = elapsedMilliseconds(connectionStartNanos, System.nanoTime());

            try (Connection connection = openedConnection) {
                failurePhase = "fixed health query execution and result validation";
                long queryStartNanos = System.nanoTime();
                try (Statement statement = connection.createStatement();
                     ResultSet resultSet = statement.executeQuery(QUERY)) {
                    querySucceeded = resultSet.next() && resultSet.getInt(1) == 1;
                } finally {
                    queryTimeMs = elapsedMilliseconds(queryStartNanos, System.nanoTime());
                }
                if (!querySucceeded) {
                    LOGGER.warning("Oracle health query completed without the expected result.");
                }
                failurePhase = "connection cleanup";
            }
            checkSucceeded = querySucceeded;
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            if (connectionTimeMs == null && connectionStartNanos != null) {
                connectionTimeMs = elapsedMilliseconds(connectionStartNanos, System.nanoTime());
            }
            LOGGER.warning("Oracle database health check could not start because required server settings are unavailable.");
        } catch (SQLException exception) {
            if (connectionTimeMs == null && connectionStartNanos != null) {
                connectionTimeMs = elapsedMilliseconds(connectionStartNanos, System.nanoTime());
            }
            sqlFailure = exception;
        } finally {
            totalTimeMs = elapsedMilliseconds(totalStartNanos, System.nanoTime());
        }

        if (sqlFailure != null) {
            SafeSqlDiagnostics.log(LOGGER, "DatabaseStatusServlet", failurePhase, sqlFailure);
        }
        return new HealthCheckResult(checkSucceeded, querySucceeded,
                connectionTimeMs, queryTimeMs, totalTimeMs);
    }

    private BigDecimal elapsedMilliseconds(long startNanos, long endNanos) {
        long elapsedNanos = Math.max(0L, endNanos - startNanos);
        return BigDecimal.valueOf(elapsedNanos, 6);
    }

    private record HealthCheckResult(boolean connected, boolean querySucceeded,
            BigDecimal connectionTimeMs, BigDecimal queryTimeMs, BigDecimal totalTimeMs) {
    }
}
