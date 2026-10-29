package edu.hostel.mess.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.db.SafeSqlDiagnostics;
import edu.hostel.mess.web.security.AuthenticationRepository;
import edu.hostel.mess.web.security.CsrfTokens;
import edu.hostel.mess.web.security.LoginRateLimiter;
import edu.hostel.mess.web.security.PasswordHashing;
import edu.hostel.mess.web.security.SecurityFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Public registration is deliberately limited to active READ_ONLY accounts. */
@WebServlet(urlPatterns = { "/signup", "/register" })
public final class RegistrationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/signup.jsp";
    private static final Logger LOGGER = Logger.getLogger(RegistrationServlet.class.getName());
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._@-]{2,63}");
    private static final String GENERIC_CREATE_ERROR =
            "We couldn't create that account. Check the details or contact the administrator.";

    private final AuthenticationRepository repository = new AuthenticationRepository();
    private final LoginRateLimiter rateLimiter = new LoginRateLimiter();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(SecurityFilter.SESSION_TIMEOUT_SECONDS);
        request.setAttribute(CsrfTokens.REQUEST_ATTRIBUTE, CsrfTokens.ensureToken(session));
        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String remoteAddress = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        if (rateLimiter.isLimited(remoteAddress, "signup", now)) {
            showError(request, response, "Too many registration attempts. Try again later.",
                    429);
            return;
        }
        // Count every registration request, including successful requests, to limit account creation.
        rateLimiter.recordFailure(remoteAddress, "signup", now);

        String username = normalizeUsername(request.getParameter("username"));
        String suppliedPassword = request.getParameter("password");
        String suppliedConfirmation = request.getParameter("passwordConfirmation");
        char[] password = toBoundedChars(suppliedPassword);
        char[] confirmation = toBoundedChars(suppliedConfirmation);
        try {
            if (username == null || !USERNAME_PATTERN.matcher(username).matches()
                    || password.length < PasswordHashing.MIN_PASSWORD_LENGTH
                    || password.length > PasswordHashing.MAX_PASSWORD_LENGTH
                    || !passwordsMatch(password, confirmation)) {
                showError(request, response,
                        "Enter a valid username, use a password of 12 to 128 characters, and make both passwords match.",
                        HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String encodedPassword = PasswordHashing.hash(password);
            int inserted;
            try (Connection connection = OracleConnectionFactory.openConnection()) {
                inserted = repository.createReadOnlyUser(connection, username, encodedPassword);
            }
            if (inserted != 1) {
                showError(request, response, GENERIC_CREATE_ERROR, HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            HttpSession session = request.getSession(false);
            if (session != null) {
                CsrfTokens.rotate(session);
                session.setAttribute("signupCompleted", Boolean.TRUE);
            }
            response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/login"));
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Registration is unavailable because required database settings are missing.");
            showError(request, response, "Registration is temporarily unavailable. Please try again later.",
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            // Includes duplicate-username/constraint failures. Do not expose a database error or account lookup.
            SafeSqlDiagnostics.log(LOGGER, "RegistrationServlet", "account creation", exception);
            int status = exception.getErrorCode() == 1
                    ? HttpServletResponse.SC_CONFLICT : HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            showError(request, response, GENERIC_CREATE_ERROR, status);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    private String normalizeUsername(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 64 ? normalized : null;
    }

    private char[] toBoundedChars(String value) {
        if (value == null) {
            return new char[0];
        }
        return value.length() > PasswordHashing.MAX_PASSWORD_LENGTH
                ? new char[PasswordHashing.MAX_PASSWORD_LENGTH + 1]
                : value.toCharArray();
    }

    private boolean passwordsMatch(char[] first, char[] second) {
        byte[] firstBytes = null;
        byte[] secondBytes = null;
        try {
            firstBytes = charsToBytes(first);
            secondBytes = charsToBytes(second);
            return java.security.MessageDigest.isEqual(firstBytes, secondBytes);
        } finally {
            if (firstBytes != null) {
                Arrays.fill(firstBytes, (byte) 0);
            }
            if (secondBytes != null) {
                Arrays.fill(secondBytes, (byte) 0);
            }
        }
    }

    private byte[] charsToBytes(char[] value) {
        java.nio.ByteBuffer encoded = java.nio.charset.StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return bytes;
    }

    private void showError(HttpServletRequest request, HttpServletResponse response, String message, int status)
            throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(SecurityFilter.SESSION_TIMEOUT_SECONDS);
        request.setAttribute(CsrfTokens.REQUEST_ATTRIBUTE, CsrfTokens.ensureToken(session));
        request.setAttribute("signupError", message);
        response.setStatus(status);
        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }
}
