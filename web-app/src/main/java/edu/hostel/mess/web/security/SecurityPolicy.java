package edu.hostel.mess.web.security;

import java.util.Set;

/** Central route and method policy used by the authentication filter. */
public final class SecurityPolicy {
    private static final Set<String> ADMIN_POST_PATHS = Set.of(
            "/procurement", "/goods-receipts", "/vendors", "/ingredients", "/mess-blocks",
            "/admin/data-management", "/admin/data-cleanup");

    private SecurityPolicy() { }

    public static boolean isPublicPath(String path) {
        return isLandingPath(path) || isAuthenticationPath(path) || isStaticPath(path);
    }

    public static boolean isLandingPath(String path) {
        return path == null || path.isEmpty() || "/".equals(path);
    }

    public static boolean isStaticPath(String path) {
        return "/dashboard.css".equals(path) || "/js/analytics-charts.js".equals(path)
                || "/js/auth.js".equals(path) || "/js/database-status-indicator.js".equals(path)
                || "/js/admin-data-filter.js".equals(path);
    }

    public static boolean isLoginPath(String path) {
        return "/login".equals(path);
    }

    public static boolean isSignupPath(String path) {
        return "/signup".equals(path) || "/register".equals(path);
    }

    public static boolean isAuthenticationPath(String path) {
        return isLoginPath(path) || isSignupPath(path);
    }

    public static boolean isLogoutPath(String path) {
        return "/logout".equals(path);
    }

    public static boolean requiresAdmin(String path, String method) {
        if ("/profile".equals(path)) {
            return false;
        }
        if (path != null && path.startsWith("/admin/")) {
            return true;
        }
        if (path != null && (path.equals("/database-status") || path.startsWith("/database-status/"))) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && !isLogoutPath(path) && !isAuthenticationPath(path);
    }

    public static boolean allowsStateChange(String path, String method) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return isAuthenticationPath(path) || isLogoutPath(path) || "/profile".equals(path)
                || ADMIN_POST_PATHS.contains(path);
    }
}
