package edu.hostel.mess.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CsrfTokensTest {
    @Test
    void comparesSubmittedTokenAndRejectsMissingOrDifferentValues() {
        assertTrue(CsrfTokens.isValid("session-bound-token", "session-bound-token"));
        assertFalse(CsrfTokens.isValid(null, "session-bound-token"));
        assertFalse(CsrfTokens.isValid("other-token", "session-bound-token"));
        assertFalse(CsrfTokens.isValid("x".repeat(129), "session-bound-token"));
    }
}
