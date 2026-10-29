package edu.hostel.mess.web.security;

import java.util.Set;

/** Restricts post-login destinations to known application-local routes. */
public final class SafeReturnPath {
    private static final Set<String> ALLOWED = Set.of(
            "/dashboard", "/procurement", "/goods-receipts", "/inventory", "/food-wastage",
            "/vendor-audit", "/vendors", "/ingredients", "/mess-blocks", "/database-status",
            "/admin/data-management", "/admin/data-cleanup", "/profile");

    private SafeReturnPath() { }

    public static String normalize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 120) {
            return null;
        }
        if (!candidate.startsWith("/") || candidate.startsWith("//")
                || candidate.indexOf('\\') >= 0 || candidate.indexOf(':') >= 0
                || candidate.indexOf('?') >= 0 || candidate.indexOf('#') >= 0
                || candidate.codePoints().anyMatch(Character::isISOControl)) {
            return null;
        }
        return ALLOWED.contains(candidate) ? candidate : null;
    }
}
