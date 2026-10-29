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
import edu.hostel.mess.web.security.AuthenticatedUser;
import edu.hostel.mess.web.security.AuthenticationRepository;
import edu.hostel.mess.web.security.AuthenticationRepository.Credential;
import edu.hostel.mess.web.security.CsrfTokens;
import edu.hostel.mess.web.security.LoginRateLimiter;
import edu.hostel.mess.web.security.PasswordHashing;
import edu.hostel.mess.web.security.SecurityFilter;
import edu.hostel.mess.web.security.SafeReturnPath;
import edu.hostel.mess.web.profile.UserProfileRepository;
import edu.hostel.mess.web.profile.UserProfile;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/login")
public final class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/login.jsp";
    private static final Logger LOGGER = Logger.getLogger(LoginServlet.class.getName());
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._@-]{2,63}");
    private final AuthenticationRepository repository = new AuthenticationRepository();
    private final UserProfileRepository profiles = new UserProfileRepository();
    private final LoginRateLimiter rateLimiter = new LoginRateLimiter();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(SecurityFilter.SESSION_TIMEOUT_SECONDS);
        request.setAttribute(CsrfTokens.REQUEST_ATTRIBUTE, CsrfTokens.ensureToken(session));
        request.setAttribute("loggedOut", "1".equals(request.getParameter("loggedOut")));
        request.setAttribute("returnTo", SafeReturnPath.normalize(request.getParameter("returnTo")));
        boolean registered = Boolean.TRUE.equals(session.getAttribute("signupCompleted"));
        request.setAttribute("registered", registered);
        if (registered) session.setAttribute("profileRequiredAfterLogin", Boolean.TRUE);
        session.removeAttribute("signupCompleted");
        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String username = normalizeUsername(request.getParameter("username"));
        String suppliedPassword = request.getParameter("password");
        char[] password = suppliedPassword == null ? new char[0]
                : suppliedPassword.length() > PasswordHashing.MAX_PASSWORD_LENGTH
                        ? new char[PasswordHashing.MAX_PASSWORD_LENGTH + 1]
                        : suppliedPassword.toCharArray();
        String remoteAddress = request.getRemoteAddr();
        String returnTo = SafeReturnPath.normalize(request.getParameter("returnTo"));

        try {
            long now = System.currentTimeMillis();
            if (rateLimiter.isLimited(remoteAddress, username, now)
                    || rateLimiter.isLimited(remoteAddress, "", now)) {
                showLoginError(request, response,
                        "Unable to sign in right now. Try again later.", 429);
                return;
            }

            Credential credential = null;
            boolean validUsername = username != null && USERNAME_PATTERN.matcher(username).matches();
            if (validUsername && password.length > 0 && password.length <= PasswordHashing.MAX_PASSWORD_LENGTH) {
                try (Connection connection = OracleConnectionFactory.openConnection()) {
                    credential = repository.findActiveUser(connection, username);
                }
            }

            boolean passwordMatches = credential == null
                    ? performDummyWork(password)
                    : PasswordHashing.verify(password, credential.passwordHash());
            if (credential == null || !passwordMatches || credential.role() == null) {
                long failedAt = System.currentTimeMillis();
                rateLimiter.recordFailure(remoteAddress, username, failedAt);
                if (username != null && !username.isEmpty()) {
                    rateLimiter.recordFailure(remoteAddress, "", failedAt);
                }
                showLoginError(request, response,
                        "The username or password is incorrect.", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            rateLimiter.clear(remoteAddress, username);
            rateLimiter.clear(remoteAddress, "");
            boolean profileRequired = Boolean.TRUE.equals(
                    request.getSession(false) == null ? null
                            : request.getSession(false).getAttribute("profileRequiredAfterLogin"));
            ProfileLoginState profileState = profileState(credential.userId());
            boolean profileIncomplete = profileRequired || !profileState.complete();
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            session.setMaxInactiveInterval(SecurityFilter.SESSION_TIMEOUT_SECONDS);
            session.setAttribute(SecurityFilter.USER_SESSION_ATTRIBUTE,
                    new AuthenticatedUser(credential.userId(), credential.username(), credential.role()));
            if (profileState.displayName() != null) session.setAttribute("profileDisplayName", profileState.displayName());
            else session.removeAttribute("profileDisplayName");
            session.removeAttribute("profileRequiredAfterLogin");
            CsrfTokens.rotate(session);
            String path = profileIncomplete ? "/profile" : returnTo == null ? "/dashboard" : returnTo;
            String destination = request.getContextPath() + path;
            response.sendRedirect(response.encodeRedirectURL(destination));
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Sign-in is unavailable because required database settings are missing.");
            showLoginError(request, response,
                    "Sign-in is temporarily unavailable. Please try again later.",
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            SafeSqlDiagnostics.log(LOGGER, "LoginServlet", "account lookup", exception);
            showLoginError(request, response,
                    "Sign-in is temporarily unavailable. Please try again later.",
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private ProfileLoginState profileState(long userId) {
        try (Connection connection = OracleConnectionFactory.openConnection()) {
            UserProfile profile = profiles.find(connection, userId);
            return new ProfileLoginState(profile != null && profile.isComplete(),
                    profile == null || profile.fullName() == null || profile.fullName().isBlank()
                            ? null : profile.fullName());
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Profile completion could not be checked; sign-in will continue without the profile redirect.");
            return new ProfileLoginState(true, null);
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 942) {
                LOGGER.warning("Profile completion check skipped because the user profile migration is not installed.");
            } else {
                SafeSqlDiagnostics.log(LOGGER, "LoginServlet", "profile completion lookup", exception);
            }
            return new ProfileLoginState(true, null);
        }
    }

    private record ProfileLoginState(boolean complete, String displayName) { }

    private String normalizeUsername(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 64 ? normalized : null;
    }

    private boolean performDummyWork(char[] password) {
        PasswordHashing.performDummyWork(password);
        return false;
    }

    private void showLoginError(HttpServletRequest request, HttpServletResponse response,
            String message, int status) throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(SecurityFilter.SESSION_TIMEOUT_SECONDS);
        request.setAttribute(CsrfTokens.REQUEST_ATTRIBUTE, CsrfTokens.ensureToken(session));
        request.setAttribute("loginError", message);
        request.setAttribute("loggedOut", Boolean.FALSE);
        request.setAttribute("returnTo", SafeReturnPath.normalize(request.getParameter("returnTo")));
        response.setStatus(status);
        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }
}
