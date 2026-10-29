package edu.hostel.mess.web.db;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Server-side JDBC diagnostics with credentials and SQL data redacted. */
public final class SafeSqlDiagnostics {
    private static final Pattern JDBC_URL = Pattern.compile("(?i)jdbc:[^\\r\\n]+");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)\\b(?:user(?:name)?|password|passwd|pwd|secret|token)\\s*[:=]\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,;\\s]+)");
    private static final Pattern REFERENCE = Pattern.compile(
            "(?i)\\b(?:invoice(?:[_ -]?reference)?|inv(?:oice)?[_ -]?ref)\\s*[:=]\\s*[^,;\\s]+");
    private static final Pattern REFERENCE_TOKEN = Pattern.compile(
            "(?i)\\b(?:INV|INVOICE)[-_/][A-Z0-9][A-Z0-9/_-]*\\b");
    private static final Pattern SQL_TEXT = Pattern.compile(
            "(?i)\\bSQL(?:\\s+statement)?\\s*[:=]\\s*.+$");
    private static final Pattern PARAMETER_VALUE = Pattern.compile(
            "(?i)\\b(?:parameter|bind(?:\\s+variable|\\s+value)?|record\\s+value|literal)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^,;\\r\\n]+)");
    private static final int MAX_THROWABLES = 12;
    private static final int MAX_STACK_FRAMES = 20;

    private SafeSqlDiagnostics() {
    }

    public static void log(Logger logger, String component, String phase, SQLException failure) {
        Deque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        int diagnostic = 0;
        while (!pending.isEmpty() && diagnostic < MAX_THROWABLES) {
            Throwable current = pending.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            diagnostic++;
            String type = current.getClass().getName();
            String message = sanitize(current.getMessage());
            if (current instanceof SQLException sqlException) {
                logger.log(Level.WARNING,
                        "{0} JDBC failure phase={1}, diagnostic={2}, exceptionClass={3}, SQLState={4}, vendorCode={5}, message={6}",
                        new Object[] { component, phase, diagnostic, type,
                                sqlException.getSQLState() == null ? "unavailable" : sqlException.getSQLState(),
                                sqlException.getErrorCode(), message });
                SQLException next = sqlException.getNextException();
                if (next != null) {
                    pending.addLast(next);
                }
            } else {
                logger.log(Level.WARNING,
                        "{0} JDBC cause phase={1}, diagnostic={2}, exceptionClass={3}, message={4}",
                        new Object[] { component, phase, diagnostic, type, message });
            }

            StackTraceElement[] frames = current.getStackTrace();
            int limit = Math.min(frames.length, MAX_STACK_FRAMES);
            for (int index = 0; index < limit; index++) {
                logger.log(Level.WARNING, "{0} JDBC stack phase={1}, diagnostic={2}, frame={3}",
                        new Object[] { component, phase, diagnostic, frames[index].toString() });
            }
            if (frames.length > limit) {
                logger.log(Level.WARNING, "{0} JDBC stack phase={1}, diagnostic={2}: additional frames omitted",
                        new Object[] { component, phase, diagnostic });
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.addLast(suppressed);
            }
            Throwable cause = current.getCause();
            if (cause != null) {
                pending.addLast(cause);
            }
        }
        if (!pending.isEmpty()) {
            logger.log(Level.WARNING, "{0} JDBC diagnostics truncated after {1} chained exceptions",
                    new Object[] { component, MAX_THROWABLES });
        }
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "unavailable";
        }
        String safe = message;
        safe = redactConfiguredValue(safe, "FREESQL_JDBC_URL", false);
        safe = redactConfiguredValue(safe, "FREESQL_DB_USER", true);
        safe = redactConfiguredValue(safe, "FREESQL_DB_PASSWORD", false);
        safe = JDBC_URL.matcher(safe).replaceAll("[REDACTED_JDBC_URL]");
        safe = CREDENTIAL.matcher(safe).replaceAll("[REDACTED_CREDENTIAL]");
        safe = REFERENCE.matcher(safe).replaceAll("[REDACTED_REFERENCE]");
        safe = REFERENCE_TOKEN.matcher(safe).replaceAll("[REDACTED_REFERENCE]");
        safe = SQL_TEXT.matcher(safe).replaceAll("SQL=[REDACTED]");
        safe = PARAMETER_VALUE.matcher(safe).replaceAll("[REDACTED_PARAMETER]");
        safe = safe.replace('\r', ' ').replace('\n', ' ');
        return safe;
    }

    private static String redactConfiguredValue(String message, String variableName, boolean ignoreCase) {
        String configuredValue = System.getenv(variableName);
        if (configuredValue == null || configuredValue.isBlank()) {
            return message;
        }
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        Pattern secret = Pattern.compile(Pattern.quote(configuredValue), flags);
        return secret.matcher(message).replaceAll("[REDACTED]");
    }
}
