package edu.hostel.mess.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PasswordHashingTest {
    @Test
    void verifiesPasswordAndUsesIndependentSalts() {
        char[] password = "Correct Horse Battery!".toCharArray();
        char[] wrongPassword = "Incorrect Horse Battery!".toCharArray();
        try {
            String first = PasswordHashing.hash(password);
            String second = PasswordHashing.hash(password);

            assertTrue(first.startsWith("argon2id$v=19$m=19456,t=2,p=1$"));
            assertNotEquals(first, second);
            assertTrue(PasswordHashing.verify(password, first));
            assertFalse(PasswordHashing.verify(wrongPassword, first));
            assertFalse(first.contains(new String(password)));
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(wrongPassword, '\0');
        }
    }

    @Test
    void rejectsMalformedHashAndOutOfRangePassword() {
        char[] password = "Correct Horse Battery!".toCharArray();
        char[] shortPassword = "short".toCharArray();
        try {
            assertFalse(PasswordHashing.verify(password, "not-a-valid-hash"));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> PasswordHashing.hash(shortPassword));
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(shortPassword, '\0');
        }
    }
}
