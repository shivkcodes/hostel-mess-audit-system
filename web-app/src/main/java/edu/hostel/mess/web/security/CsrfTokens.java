package edu.hostel.mess.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpSession;

public final class CsrfTokens {
    public static final String SESSION_ATTRIBUTE = CsrfTokens.class.getName() + ".token";
    public static final String REQUEST_ATTRIBUTE = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private CsrfTokens() { }

    public static String ensureToken(HttpSession session) {
        Object current = session.getAttribute(SESSION_ATTRIBUTE);
        if (current instanceof String token && token.length() >= 40 && token.length() <= 64) {
            return token;
        }
        return rotate(session);
    }

    public static String rotate(HttpSession session) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        java.util.Arrays.fill(randomBytes, (byte) 0);
        session.setAttribute(SESSION_ATTRIBUTE, token);
        return token;
    }

    public static boolean isValid(String submitted, String expected) {
        if (submitted == null || expected == null || submitted.length() > 128 || expected.length() > 128) {
            return false;
        }
        return MessageDigest.isEqual(submitted.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
