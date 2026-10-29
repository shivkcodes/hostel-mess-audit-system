package edu.hostel.mess.web.security;

import java.util.EnumSet;

import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public final class SecurityConfigurationListener implements ServletContextListener {
    private static final String SECURE_COOKIE_ENV = "HOSTEL_SESSION_COOKIE_SECURE";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        var context = event.getServletContext();
        context.setSessionTimeout(15);
        context.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));

        SessionCookieConfig cookie = context.getSessionCookieConfig();
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookieSetting());
        cookie.setAttribute("SameSite", "Lax");
    }

    private boolean secureCookieSetting() {
        String configured = System.getenv(SECURE_COOKIE_ENV);
        if (configured == null || configured.isBlank()) {
            return true;
        }
        if ("true".equalsIgnoreCase(configured.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(configured.trim())) {
            return false;
        }
        throw new IllegalStateException(SECURE_COOKIE_ENV + " must be set to true or false.");
    }
}
