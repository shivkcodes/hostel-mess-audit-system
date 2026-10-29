package edu.hostel.mess.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.db.SafeSqlDiagnostics;
import edu.hostel.mess.web.profile.UserProfile;
import edu.hostel.mess.web.profile.ProfileSaveOutcome;
import edu.hostel.mess.web.profile.UserProfileRepository;
import edu.hostel.mess.web.profile.UserProfileValidator;
import edu.hostel.mess.web.profile.UserProfileValidator.FormData;
import edu.hostel.mess.web.profile.UserProfileValidator.ValidationException;
import edu.hostel.mess.web.security.AuthenticatedUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/profile")
public final class UserProfileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(UserProfileServlet.class.getName());
    private static final String JSP = "/WEB-INF/views/profile.jsp";
    private final UserProfileRepository repository = new UserProfileRepository();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        AuthenticatedUser user = authenticatedUser(request, response);
        if (user == null) return;
        try (Connection connection = OracleConnectionFactory.openConnection()) {
            UserProfile profile = repository.find(connection, user.userId());
            request.setAttribute("profile", profile);
            FormData form = profile == null ? emptyForm() : FormData.from(profile);
            request.setAttribute("profileForm", form);
            request.setAttribute("profileMissingFields", UserProfileValidator.missingRequiredFields(form));
            request.setAttribute("profileComplete", profile != null && profile.isComplete());
            if (profile != null && profile.fullName() != null) {
                request.getSession(false).setAttribute("profileDisplayName", profile.fullName());
            }
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            renderUnavailable(request, response, "Profile information is temporarily unavailable.");
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 942) {
                LOGGER.warning("Profile page unavailable because the user profile migration has not been applied.");
                request.setAttribute("profileMigrationRequired", Boolean.TRUE);
                request.setAttribute("profileError", "Profile setup is pending. An administrator must apply database/07_user_profiles.sql before profile details can be saved.");
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                request.getRequestDispatcher(JSP).forward(request, response);
                return;
            }
            SafeSqlDiagnostics.log(LOGGER, "UserProfileServlet", "profile read", exception);
            renderUnavailable(request, response, "Profile information is temporarily unavailable.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        AuthenticatedUser user = authenticatedUser(request, response);
        if (user == null) return;
        FormData form = readForm(request);
        request.setAttribute("profileForm", form);
        var missingFields = UserProfileValidator.missingRequiredFields(form);
        request.setAttribute("profileMissingFields", missingFields);
        request.setAttribute("profileComplete", missingFields.isEmpty());
        try {
            UserProfile profile = UserProfileValidator.validate(form);
            if (!profile.isComplete()) {
                request.setAttribute("profileError", "Complete the required fields listed below before saving.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                request.getRequestDispatcher(JSP).forward(request, response);
                return;
            }
            boolean wasComplete;
            try (Connection connection = OracleConnectionFactory.openConnection()) {
                UserProfile previous = repository.find(connection, user.userId());
                wasComplete = previous != null && previous.isComplete();
                repository.save(connection, user.userId(), profile);
            }
            ProfileSaveOutcome outcome = ProfileSaveOutcome.afterSave(wasComplete, profile);
            if (!outcome.complete()) {
                request.setAttribute("profileError", "Complete the required fields listed below before saving.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                request.getRequestDispatcher(JSP).forward(request, response);
                return;
            }
            request.getSession(false).setAttribute("profileDisplayName", profile.fullName());
            request.getSession(false).setAttribute(ProfileSaveOutcome.SESSION_ATTRIBUTE, outcome.flashCode());
            response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + outcome.redirectPath()));
        } catch (ValidationException exception) {
            request.setAttribute("profileComplete", Boolean.FALSE);
            request.setAttribute("profileError", exception.getMessage());
            request.setAttribute("profileMissingFields", UserProfileValidator.missingRequiredFields(form));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            renderUnavailable(request, response, "Your profile could not be saved right now.");
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 942) {
                request.setAttribute("profileMigrationRequired", Boolean.TRUE);
                request.setAttribute("profileError", "Profile setup is pending. Ask an administrator to apply database/07_user_profiles.sql.");
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                request.getRequestDispatcher(JSP).forward(request, response);
                return;
            }
            SafeSqlDiagnostics.log(LOGGER, "UserProfileServlet", "profile save", exception);
            renderUnavailable(request, response, "Your profile could not be saved right now.");
        }
    }

    private AuthenticatedUser authenticatedUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Object value = request.getAttribute("authenticatedUser");
        if (value instanceof AuthenticatedUser user) return user;
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return null;
    }

    private FormData readForm(HttpServletRequest request) {
        return new FormData(value(request, "fullName", 130), value(request, "emailAddress", 260),
                value(request, "phoneNumber", 40), value(request, "addressLine", 250),
                value(request, "city", 110), value(request, "stateName", 110),
                value(request, "postalCode", 30), value(request, "institutionName", 170),
                value(request, "courseName", 110), value(request, "departmentName", 110),
                value(request, "semesterNumber", 8), value(request, "studyYear", 8),
                value(request, "enrollmentId", 90), value(request, "graduationYear", 8));
    }

    private String value(HttpServletRequest request, String name, int max) {
        String value = request.getParameter(name);
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private FormData emptyForm() {
        return new FormData("", "", "", "", "", "", "", "", "", "", "", "", "", "");
    }

    private void renderUnavailable(HttpServletRequest request, HttpServletResponse response, String message)
            throws ServletException, IOException {
        request.setAttribute("profileError", message);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        request.getRequestDispatcher(JSP).forward(request, response);
    }
}
