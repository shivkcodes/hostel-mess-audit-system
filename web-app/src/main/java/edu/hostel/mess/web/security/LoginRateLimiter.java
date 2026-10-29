package edu.hostel.mess.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, per-process fixed-window login throttling keyed by a digest of client and username. */
public final class LoginRateLimiter {
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final long DEFAULT_WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_KEYS = 10_000;

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>(256, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AttemptWindow> eldest) {
            return size() > MAX_KEYS;
        }
    };

    public LoginRateLimiter() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW_MILLIS);
    }

    LoginRateLimiter(int maxAttempts, long windowMillis) {
        if (maxAttempts < 1 || windowMillis < 1) {
            throw new IllegalArgumentException("Rate-limit settings must be positive.");
        }
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
    }

    public synchronized boolean isLimited(String remoteAddress, String normalizedUsername, long nowMillis) {
        String key = key(remoteAddress, normalizedUsername);
        AttemptWindow window = attempts.get(key);
        if (window == null) {
            return false;
        }
        if (nowMillis - window.startedAtMillis >= windowMillis || nowMillis < window.startedAtMillis) {
            attempts.remove(key);
            return false;
        }
        return window.failures >= maxAttempts;
    }

    public synchronized void recordFailure(String remoteAddress, String normalizedUsername, long nowMillis) {
        String key = key(remoteAddress, normalizedUsername);
        AttemptWindow window = attempts.get(key);
        if (window == null || nowMillis - window.startedAtMillis >= windowMillis
                || nowMillis < window.startedAtMillis) {
            attempts.put(key, new AttemptWindow(nowMillis, 1));
        } else {
            window.failures++;
        }
    }

    public synchronized void clear(String remoteAddress, String normalizedUsername) {
        attempts.remove(key(remoteAddress, normalizedUsername));
    }

    private String key(String remoteAddress, String username) {
        String input = (remoteAddress == null ? "unknown" : remoteAddress) + "\u0000"
                + (username == null ? "" : username);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(value & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required digest algorithm is unavailable.", exception);
        }
    }

    private static final class AttemptWindow {
        private final long startedAtMillis;
        private int failures;

        private AttemptWindow(long startedAtMillis, int failures) {
            this.startedAtMillis = startedAtMillis;
            this.failures = failures;
        }
    }
}
