package edu.hostel.mess.web.security;

import java.util.Locale;

public enum UserRole {
    ADMIN,
    READ_ONLY;

    public static UserRole fromDatabase(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
