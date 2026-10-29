package edu.hostel.mess.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    @Test
    void limitsRepeatedFailuresAndResetsAfterWindowOrSuccess() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, 1_000L);
        limiter.recordFailure("127.0.0.1", "admin", 10_000L);
        assertFalse(limiter.isLimited("127.0.0.1", "admin", 10_001L));
        limiter.recordFailure("127.0.0.1", "admin", 10_002L);

        assertTrue(limiter.isLimited("127.0.0.1", "admin", 10_003L));
        assertFalse(limiter.isLimited("127.0.0.2", "admin", 10_003L));
        assertFalse(limiter.isLimited("127.0.0.1", "admin", 11_002L));

        limiter.recordFailure("127.0.0.1", "admin", 12_000L);
        limiter.clear("127.0.0.1", "admin");
        assertFalse(limiter.isLimited("127.0.0.1", "admin", 12_001L));
    }

    @Test
    void supportsAddressWideLimitsAcrossDifferentUsernames() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, 1_000L);
        limiter.recordFailure("127.0.0.1", "first", 10_000L);
        limiter.recordFailure("127.0.0.1", "", 10_000L);
        limiter.recordFailure("127.0.0.1", "second", 10_001L);
        limiter.recordFailure("127.0.0.1", "", 10_001L);

        assertTrue(limiter.isLimited("127.0.0.1", "", 10_002L));
        assertFalse(limiter.isLimited("127.0.0.2", "", 10_002L));
    }
}
