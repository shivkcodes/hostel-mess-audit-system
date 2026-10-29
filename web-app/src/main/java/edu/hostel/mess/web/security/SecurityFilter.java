package edu.hostel.mess.web.security;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Enforces login, role, method, and CSRF policy before any application servlet executes. */
@WebFilter("/*")
public final class SecurityFilter implements Filter {
    public static final String USER_SESSION_ATTRIBUTE = SecurityFilter.class.getName() + ".user";
    public static final int SESSION_TIMEOUT_SECONDS = 15 * 60;

    @Override
    public void doFilter(ServletRequest rawRequest, ServletResponse rawResponse, FilterChain chain)
            throws IOException, ServletException {
        if (!(rawRequest instanceof HttpServletRequest request)
                || !(rawResponse instanceof HttpServletResponse response)) {
            chain.doFilter(rawRequest, rawResponse);
            return;
        }

        request.setCharacterEncoding("UTF-8");

        String path = request.getServletPath();
        request.setAttribute("activeRoute", path == null || path.isEmpty() ? "/" : path);
        String method = request.getMethod();
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        if (!SecurityPolicy.isStaticPath(path)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0L);
        }

        if (SecurityPolicy.isAuthenticationPath(path)) {
            if ("POST".equalsIgnoreCase(method) && !hasValidCsrf(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Request rejected. Reload the sign-in page and try again.");
                return;
            }
            if (!isReadMethod(method) && !"POST".equalsIgnoreCase(method)) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (SecurityPolicy.isStaticPath(path)) {
            if (!isReadMethod(method)) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (SecurityPolicy.isLandingPath(path)) {
            if (!isReadMethod(method)) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        Object sessionUser = session == null ? null : session.getAttribute(USER_SESSION_ATTRIBUTE);
        AuthenticatedUser user = sessionUser instanceof AuthenticatedUser authenticatedUser
                ? authenticatedUser : null;
        if (user == null || user.role() == null) {
            if (isReadMethod(method)) {
                String returnPath = SafeReturnPath.normalize(path);
                String target = request.getContextPath() + "/login";
                if (returnPath != null) {
                    target += "?returnTo=" + java.net.URLEncoder.encode(returnPath,
                            java.nio.charset.StandardCharsets.UTF_8);
                }
                response.sendRedirect(response.encodeRedirectURL(target));
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "Sign in is required to perform this action.");
            }
            return;
        }

        if (SecurityPolicy.requiresAdmin(path, method) && user.role() != UserRole.ADMIN) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Your account is not authorized to perform this action.");
            return;
        }

        if ("POST".equalsIgnoreCase(method)) {
            if (!SecurityPolicy.allowsStateChange(path, method)) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            if (!SecurityPolicy.isLogoutPath(path) && !"/profile".equals(path)
                    && user.role() != UserRole.ADMIN) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Your account is not authorized to perform this action.");
                return;
            }
            if (!hasValidCsrf(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Request rejected. Return to the form, refresh it, and try again.");
                return;
            }
        } else if (!isReadMethod(method)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        String token = CsrfTokens.ensureToken(session);
        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
        request.setAttribute(CsrfTokens.REQUEST_ATTRIBUTE, token);
        request.setAttribute("authenticatedUser", user);
        request.setAttribute("canManage", user.role() == UserRole.ADMIN);
        Object profileName = session.getAttribute("profileDisplayName");
        String headerName = user.username();
        if (user.role() == UserRole.READ_ONLY && profileName instanceof String name && !name.isBlank()) {
            headerName = name;
        }
        request.setAttribute("headerDisplayName", headerName);
        chain.doFilter(request, response);
    }

    private boolean hasValidCsrf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(CsrfTokens.SESSION_ATTRIBUTE);
        String supplied = request.getParameter("_csrf");
        if (supplied == null) {
            supplied = request.getHeader("X-CSRF-Token");
        }
        return expected instanceof String token && CsrfTokens.isValid(supplied, token);
    }

    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
