package edu.hostel.mess.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import edu.hostel.mess.web.security.AuthenticatedUser;
import edu.hostel.mess.web.security.SecurityFilter;
import edu.hostel.mess.web.security.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

class SecurityFilterAdminRoutesTest {
    private static final String CONTEXT_PATH = "/hostel-mess-web";
    private static final String[] ADMIN_GET_ROUTES = { "/admin/data-management", "/admin/data-cleanup" };

    @Test
    void adminCanReachBothAdminGetRoutes() throws Exception {
        for (String path : ADMIN_GET_ROUTES) {
            Fixture fixture = new Fixture(path, UserRole.ADMIN);
            AtomicBoolean reachedServlet = new AtomicBoolean();

            new SecurityFilter().doFilter(fixture.request, fixture.response,
                    (ServletRequest ignoredRequest, ServletResponse ignoredResponse) -> reachedServlet.set(true));

            assertTrue(reachedServlet.get(), path);
            assertNull(fixture.errorStatus.get(), path);
            assertNotNull(fixture.requestAttributes.get(CsrfTokens.REQUEST_ATTRIBUTE), path);
            assertEquals(path, fixture.requestAttributes.get("activeRoute"), path);
            assertEquals(SecurityFilter.SESSION_TIMEOUT_SECONDS, fixture.sessionTimeout.get(), path);
        }
    }

    @Test
    void readOnlyUserIsDeniedOnBothAdminGetRoutes() throws Exception {
        for (String path : ADMIN_GET_ROUTES) {
            Fixture fixture = new Fixture(path, UserRole.READ_ONLY);
            AtomicBoolean reachedServlet = new AtomicBoolean();

            new SecurityFilter().doFilter(fixture.request, fixture.response,
                    (ServletRequest ignoredRequest, ServletResponse ignoredResponse) -> reachedServlet.set(true));

            assertFalse(reachedServlet.get(), path);
            assertEquals(HttpServletResponse.SC_FORBIDDEN, fixture.errorStatus.get(), path);
        }
    }

    @Test
    void anonymousUserIsRedirectedToSignInWithSafeLocalReturnPath() throws Exception {
        for (String path : ADMIN_GET_ROUTES) {
            Fixture fixture = new Fixture(path, null);
            AtomicBoolean reachedServlet = new AtomicBoolean();

            new SecurityFilter().doFilter(fixture.request, fixture.response,
                    (ServletRequest ignoredRequest, ServletResponse ignoredResponse) -> reachedServlet.set(true));

            assertFalse(reachedServlet.get(), path);
            assertEquals(CONTEXT_PATH + "/login?returnTo=%2Fadmin%2F" + path.substring("/admin/".length()),
                    fixture.redirect.get(), path);
            assertNull(fixture.errorStatus.get(), path);
        }
    }

    @Test
    void readOnlyUserCanPostOnlyToOwnProfileWithValidSessionCsrfToken() throws Exception {
        Fixture fixture = new Fixture("/profile", UserRole.READ_ONLY, "POST", "valid-profile-token");
        AtomicBoolean reachedServlet = new AtomicBoolean();

        new SecurityFilter().doFilter(fixture.request, fixture.response,
                (ServletRequest ignoredRequest, ServletResponse ignoredResponse) -> reachedServlet.set(true));

        assertTrue(reachedServlet.get());
        assertNull(fixture.errorStatus.get());
        assertEquals("/profile", fixture.requestAttributes.get("activeRoute"));
        assertEquals(new AuthenticatedUser(7, "test-admin", UserRole.READ_ONLY),
                fixture.requestAttributes.get("authenticatedUser"));
    }

    @Test
    void readOnlyProfilePostRejectsMissingCsrfToken() throws Exception {
        Fixture fixture = new Fixture("/profile", UserRole.READ_ONLY, "POST", null);
        AtomicBoolean reachedServlet = new AtomicBoolean();

        new SecurityFilter().doFilter(fixture.request, fixture.response,
                (ServletRequest ignoredRequest, ServletResponse ignoredResponse) -> reachedServlet.set(true));

        assertFalse(reachedServlet.get());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, fixture.errorStatus.get());
    }

    @Test
    void authenticatedRolesCanOpenOwnProfileAndAnonymousUsersAreRedirected() throws Exception {
        for (UserRole role : new UserRole[] { UserRole.ADMIN, UserRole.READ_ONLY }) {
            Fixture fixture = new Fixture("/profile", role);
            AtomicBoolean reachedServlet = new AtomicBoolean();
            new SecurityFilter().doFilter(fixture.request, fixture.response,
                    (ServletRequest ignoredRequest, ServletResponse ignoredResponse) -> reachedServlet.set(true));
            assertTrue(reachedServlet.get(), role.name());
        }
        Fixture anonymous = new Fixture("/profile", null);
        AtomicBoolean reachedServlet = new AtomicBoolean();
        new SecurityFilter().doFilter(anonymous.request, anonymous.response,
                (ServletRequest ignoredRequest, ServletResponse ignoredResponse) -> reachedServlet.set(true));
        assertFalse(reachedServlet.get());
        assertEquals(CONTEXT_PATH + "/login?returnTo=%2Fprofile", anonymous.redirect.get());
    }

    @Test
    void anonymousProfilePostIsRejected() throws Exception {
        Fixture fixture = new Fixture("/profile", null, "POST", "valid-profile-token");
        AtomicBoolean reachedServlet = new AtomicBoolean();
        new SecurityFilter().doFilter(fixture.request, fixture.response,
                (ServletRequest ignoredRequest, ServletResponse ignoredResponse) -> reachedServlet.set(true));
        assertFalse(reachedServlet.get());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, fixture.errorStatus.get());
    }

    private static final class Fixture {
        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private final Map<String, Object> requestAttributes = new HashMap<>();
        private final AtomicReference<Integer> errorStatus = new AtomicReference<>();
        private final AtomicReference<String> redirect = new AtomicReference<>();
        private final AtomicInteger sessionTimeout = new AtomicInteger();
        private final HttpSession session;
        private final HttpServletRequest request;
        private final HttpServletResponse response;

        private Fixture(String path, UserRole role) {
            this(path, role, "GET", null);
        }

        private Fixture(String path, UserRole role, String httpMethod, String csrfToken) {
            if (role != null) {
                sessionAttributes.put(SecurityFilter.USER_SESSION_ATTRIBUTE,
                        new AuthenticatedUser(7, "test-admin", role));
                if (csrfToken != null) sessionAttributes.put(CsrfTokens.SESSION_ATTRIBUTE, csrfToken);
                session = proxy(HttpSession.class, (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> sessionAttributes.get(args[0]);
                    case "setAttribute" -> { sessionAttributes.put((String) args[0], args[1]); yield null; }
                    case "setMaxInactiveInterval" -> { sessionTimeout.set((Integer) args[0]); yield null; }
                    default -> defaultValue(method.getReturnType());
                });
            } else {
                session = null;
            }

            request = proxy(HttpServletRequest.class, (proxy, methodCall, args) -> switch (methodCall.getName()) {
                case "setCharacterEncoding", "setAttribute" -> {
                    if ("setAttribute".equals(methodCall.getName())) requestAttributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "getAttribute" -> requestAttributes.get(args[0]);
                case "getServletPath" -> path;
                case "getMethod" -> httpMethod;
                case "getParameter" -> "_csrf".equals(args[0]) ? csrfToken : null;
                case "getContextPath" -> CONTEXT_PATH;
                case "getSession" -> session;
                default -> defaultValue(methodCall.getReturnType());
            });

            response = proxy(HttpServletResponse.class, (proxy, method, args) -> switch (method.getName()) {
                case "sendError" -> { errorStatus.set((Integer) args[0]); yield null; }
                case "sendRedirect" -> { redirect.set((String) args[0]); yield null; }
                case "encodeRedirectURL" -> args[0];
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
