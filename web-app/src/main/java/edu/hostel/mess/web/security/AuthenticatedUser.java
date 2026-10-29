package edu.hostel.mess.web.security;

import java.io.Serial;
import java.io.Serializable;

public record AuthenticatedUser(long userId, String username, UserRole role) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
}
