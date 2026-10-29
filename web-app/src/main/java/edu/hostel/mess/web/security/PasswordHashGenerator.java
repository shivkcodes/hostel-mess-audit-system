package edu.hostel.mess.web.security;

import java.io.Console;
import java.util.Arrays;

/** Local-only provisioning helper; it is not an HTTP endpoint and never accepts command-line secrets. */
public final class PasswordHashGenerator {
    private PasswordHashGenerator() { }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Run this helper from a terminal that supports hidden password input.");
            return;
        }

        char[] first = console.readPassword("New administrator password (12-128 characters): ");
        char[] second = console.readPassword("Confirm password: ");
        try {
            if (first == null || second == null || !Arrays.equals(first, second)) {
                System.err.println("Passwords did not match.");
                return;
            }
            String encoded = PasswordHashing.hash(first);
            System.out.println("Generated password hash (store securely; never log the plaintext password):");
            System.out.println(encoded);
        } catch (IllegalArgumentException exception) {
            System.err.println("Password must contain between 12 and 128 characters.");
        } finally {
            if (first != null) {
                Arrays.fill(first, '\0');
            }
            if (second != null) {
                Arrays.fill(second, '\0');
            }
        }
    }
}
