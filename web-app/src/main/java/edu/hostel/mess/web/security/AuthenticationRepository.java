package edu.hostel.mess.web.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AuthenticationRepository {
    private static final String FIND_ACTIVE_USER = """
            SELECT user_id, username, password_hash, role_code
            FROM app_users
            WHERE username = ? AND active_flag = 'Y'
            """;
    private static final String CREATE_READ_ONLY_USER = """
            INSERT INTO app_users (username, password_hash, role_code, active_flag)
            VALUES (?, ?, 'READ_ONLY', 'Y')
            """;

    public Credential findActiveUser(Connection connection, String normalizedUsername) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_USER)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new Credential(resultSet.getLong("user_id"),
                        resultSet.getString("username"), resultSet.getString("password_hash"),
                        UserRole.fromDatabase(resultSet.getString("role_code")));
            }
        }
    }

    /** Creates a self-registered account with the fixed least-privilege role. */
    public int createReadOnlyUser(Connection connection, String normalizedUsername, String passwordHash)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CREATE_READ_ONLY_USER)) {
            statement.setString(1, normalizedUsername);
            statement.setString(2, passwordHash);
            return statement.executeUpdate();
        }
    }

    public record Credential(long userId, String username, String passwordHash, UserRole role) { }
}
