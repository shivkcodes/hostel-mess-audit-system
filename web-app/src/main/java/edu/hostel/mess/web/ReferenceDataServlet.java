package edu.hostel.mess.web;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.hostel.mess.web.db.OracleConnectionFactory;
import edu.hostel.mess.web.db.SafeSqlDiagnostics;
import edu.hostel.mess.web.management.ReferenceDataRepository;
import edu.hostel.mess.web.management.ReferenceDataRepository.Dependency;
import edu.hostel.mess.web.management.ReferenceDataRepository.Directory;
import edu.hostel.mess.web.management.ReferenceDataRepository.DirectoryRecord;
import edu.hostel.mess.web.management.ReferenceDataRepository.FormInput;
import edu.hostel.mess.web.management.ReferenceDataRepository.ManagementException;
import edu.hostel.mess.web.management.ReferenceDataRepository.PageData;
import edu.hostel.mess.web.management.ReferenceDataValidator;
import edu.hostel.mess.web.management.ReferenceDataValidator.ValidationException;
import edu.hostel.mess.web.security.AuthenticatedUser;
import edu.hostel.mess.web.security.SecurityFilter;
import edu.hostel.mess.web.security.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Reference-data directory pages and ADMIN-only CRUD operations. */
@WebServlet(urlPatterns = { "/vendors", "/ingredients", "/mess-blocks" })
public class ReferenceDataServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JSP_PATH = "/WEB-INF/views/reference-data.jsp";
    private static final Logger LOGGER = Logger.getLogger(ReferenceDataServlet.class.getName());
    private static final String FLASH_MESSAGE = "referenceFlashMessage";
    private static final String FLASH_TYPE = "referenceFlashType";
    private final ReferenceDataRepository repository = new ReferenceDataRepository();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Directory directory = Directory.fromPath(request.getServletPath());
        if (directory == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String search = parseSearch(request.getParameter("q"), request);
        Long selectedId = parseId(request.getParameter("id"), request);
        boolean canManage = isAdmin(request);
        boolean createMode = canManage && "1".equals(request.getParameter("new"));
        boolean editMode = canManage && "1".equals(request.getParameter("edit"));
        boolean confirmDeleteMode = canManage && "1".equals(request.getParameter("confirmDelete"));
        consumeFlash(request);
        renderPage(request, response, directory, search, selectedId,
                createMode, editMode, confirmDeleteMode, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Directory directory = Directory.fromPath(request.getServletPath());
        if (directory == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!isAdmin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Administrator permission is required for reference-data changes.");
            return;
        }

        request.setCharacterEncoding("UTF-8");
        AuthenticatedUser actor = (AuthenticatedUser) request.getAttribute("authenticatedUser");
        String actorName = actor == null ? "administrator" : actor.username();
        String operation = request.getParameter("operation");
        String search = parseSearch(request.getParameter("q"), request);
        boolean createMode = "create".equals(operation);
        boolean editMode = "update".equals(operation);
        Long id = needsId(operation) ? parseRequiredId(request.getParameter("recordId")) : null;
        Map<String, String> submittedValues = readFormValues(request, directory);

        if (operation == null || (!createMode && !editMode && !"activate".equals(operation)
                && !"deactivate".equals(operation) && !"delete".equals(operation))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderPage(request, response, directory, search, null, false, false, false,
                    "Choose a supported management action.", null);
            return;
        }
        if (needsId(operation) && id == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderPage(request, response, directory, search, null, false, false, false,
                    "Choose a valid record ID.", submittedValues);
            return;
        }
        if ("delete".equals(operation) && !"yes".equals(request.getParameter("confirmDelete"))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderPage(request, response, directory, search, id, false, false, true,
                    "Confirm the deletion before continuing.", null);
            return;
        }

        try (Connection connection = OracleConnectionFactory.openConnection()) {
            switch (operation) {
                case "create" -> repository.create(connection, directory, validatedInput(directory, request), actorName);
                case "update" -> repository.update(connection, directory, id,
                        validatedInput(directory, request), actorName);
                case "activate" -> {
                    boolean changed = repository.setActive(connection, directory, id, true, actorName);
                    setFlash(request, changed ? title(directory) + " activated." : title(directory) + " was already active.",
                            changed ? "success" : "info");
                    redirect(request, response, directory, search);
                    return;
                }
                case "deactivate" -> {
                    boolean changed = repository.setActive(connection, directory, id, false, actorName);
                    setFlash(request, changed ? title(directory) + " deactivated." : title(directory) + " was already inactive.",
                            changed ? "success" : "info");
                    redirect(request, response, directory, search);
                    return;
                }
                case "delete" -> repository.delete(connection, directory, id, actorName);
                default -> throw new IllegalStateException("Unsupported operation passed validation.");
            }
            setFlash(request, createMode ? title(directory) + " created."
                    : editMode ? title(directory) + " updated." : title(directory) + " deleted.", "success");
            redirect(request, response, directory, search);
        } catch (ValidationException exception) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderPage(request, response, directory, search, id, createMode, editMode, false,
                    exception.getMessage(), submittedValues);
        } catch (ManagementException exception) {
            response.setStatus(exception.getStatusCode());
            renderPage(request, response, directory, search, id, createMode, editMode,
                    "delete".equals(operation), exception.getMessage(), submittedValues);
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Reference-data mutation is unavailable because database settings are missing.");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            renderPage(request, response, directory, search, id, createMode, editMode, false,
                    "Reference data is temporarily unavailable. Please try again later.", submittedValues);
        } catch (SQLException exception) {
            SafeSqlDiagnostics.log(LOGGER, "ReferenceDataServlet", "reference-data mutation", exception);
            String message = exception.getErrorCode() == 1
                    ? "That code is already in use. Choose a different code."
                    : exception.getErrorCode() == 2292 && "delete".equals(operation)
                            ? directory.blockedDeleteMessage()
                            : "Reference data could not be saved. Check the submitted details or try again later.";
            response.setStatus(exception.getErrorCode() == 1 || exception.getErrorCode() == 2292
                    ? HttpServletResponse.SC_CONFLICT : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            renderPage(request, response, directory, search, id, createMode, editMode,
                    "delete".equals(operation), message, submittedValues);
        }
    }

    private FormInput validatedInput(Directory directory, HttpServletRequest request) throws ValidationException {
        return ReferenceDataValidator.validate(directory,
                request.getParameter("code"), request.getParameter("name"),
                request.getParameter("contact_name"), request.getParameter("phone"),
                request.getParameter("email"), request.getParameter("base_unit"));
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, Directory directory,
            String search, Long selectedId, boolean createMode, boolean editMode, boolean confirmDeleteMode,
            String inputError, Map<String, String> submittedValues) throws ServletException, IOException {
        request.setAttribute("directory", directory);
        request.setAttribute("directoryPath", directory.path());
        request.setAttribute("directoryLabel", directory.label());
        request.setAttribute("directorySingular", directory.singular());
        request.setAttribute("search", search == null ? "" : search);
        request.setAttribute("canManage", isAdmin(request));
        request.setAttribute("createMode", createMode);
        request.setAttribute("editMode", editMode);
        request.setAttribute("confirmDeleteMode", confirmDeleteMode);
        request.setAttribute("referenceAvailable", Boolean.FALSE);
        request.setAttribute("directoryRecords", List.of());
        request.setAttribute("dependencies", List.of());
        request.setAttribute("selectedIdProvided", selectedId != null);
        if (inputError != null) {
            request.setAttribute("inputError", inputError);
        }

        try (Connection connection = OracleConnectionFactory.openConnection()) {
            PageData pageData = repository.loadPage(connection, directory, search, selectedId);
            DirectoryRecord selected = pageData.selectedRecord();
            request.setAttribute("directoryRecords", pageData.records());
            request.setAttribute("selectedRecord", selected);
            request.setAttribute("dependencies", pageData.dependencies());
            request.setAttribute("selectedIdProvided", selectedId != null);
            request.setAttribute("hasDependencies", pageData.dependencies().stream().anyMatch(d -> d.count() > 0));
            request.setAttribute("referenceAvailable", Boolean.TRUE);

            Map<String, String> values = submittedValues;
            if (values == null && editMode && selected != null) {
                values = selected.fields();
            }
            if (values == null) {
                values = emptyFormValues(directory);
            }
            request.setAttribute("formValues", values);
        } catch (OracleConnectionFactory.MissingDatabaseConfigurationException exception) {
            LOGGER.warning("Reference data directory is unavailable because database settings are missing.");
            request.setAttribute("referenceError", "Reference data is temporarily unavailable.");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException exception) {
            SafeSqlDiagnostics.log(LOGGER, "ReferenceDataServlet", "directory query", exception);
            request.setAttribute("referenceError", "Reference data is temporarily unavailable.");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        request.getRequestDispatcher(JSP_PATH).forward(request, response);
    }

    private Map<String, String> emptyFormValues(Directory directory) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", "");
        values.put("name", "");
        values.put("contact_name", "");
        values.put("phone", "");
        values.put("email", "");
        values.put("base_unit", directory == Directory.INGREDIENTS ? "KG" : "");
        return Map.copyOf(values);
    }

    private Map<String, String> readFormValues(HttpServletRequest request, Directory directory) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", bounded(request.getParameter("code"), 21));
        values.put("name", bounded(request.getParameter("name"), 121));
        if (directory == Directory.VENDORS) {
            values.put("contact_name", bounded(request.getParameter("contact_name"), 101));
            values.put("phone", bounded(request.getParameter("phone"), 31));
            values.put("email", bounded(request.getParameter("email"), 255));
        } else if (directory == Directory.INGREDIENTS) {
            values.put("base_unit", bounded(request.getParameter("base_unit"), 11));
        }
        return Map.copyOf(values);
    }

    private String bounded(String value, int maximum) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximum ? trimmed : trimmed.substring(0, maximum);
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(SecurityFilter.USER_SESSION_ATTRIBUTE);
        return value instanceof AuthenticatedUser user && user.role() == UserRole.ADMIN;
    }

    private boolean needsId(String operation) {
        return "update".equals(operation) || "activate".equals(operation)
                || "deactivate".equals(operation) || "delete".equals(operation);
    }

    private Long parseRequiredId(String value) {
        if (value == null || value.isBlank() || value.length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseId(String value, HttpServletRequest request) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Long id = parseRequiredId(value);
        if (id == null) {
            request.setAttribute("inputError", "Choose a valid record ID.");
        }
        return id;
    }

    private String parseSearch(String value, HttpServletRequest request) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            request.setAttribute("inputError", "Search text must be 100 characters or fewer.");
            return null;
        }
        return normalized;
    }

    private void consumeFlash(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object message = session.getAttribute(FLASH_MESSAGE);
        Object type = session.getAttribute(FLASH_TYPE);
        if (message instanceof String text && type instanceof String noticeType) {
            request.setAttribute("mutationNotice", text);
            request.setAttribute("mutationNoticeType", noticeType);
        }
        session.removeAttribute(FLASH_MESSAGE);
        session.removeAttribute(FLASH_TYPE);
    }

    private void setFlash(HttpServletRequest request, String message, String type) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(FLASH_MESSAGE, message);
            session.setAttribute(FLASH_TYPE, type);
        }
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, Directory directory, String search)
            throws IOException {
        String target = request.getContextPath() + directory.path();
        if (search != null && !search.isBlank()) {
            target += "?q=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
        }
        response.sendRedirect(response.encodeRedirectURL(target));
    }

    private String title(Directory directory) {
        return directory.singular().substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + directory.singular().substring(1);
    }
}
