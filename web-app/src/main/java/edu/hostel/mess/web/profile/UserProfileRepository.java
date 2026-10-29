package edu.hostel.mess.web.profile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** Reads and updates only the signed-in user's own profile row. */
public final class UserProfileRepository {
    public UserProfile find(Connection connection, long userId) throws SQLException {
        String sql = """
                SELECT full_name, email_address, phone_number, address_line, city, state_name,
                       postal_code, institution_name, course_name, department_name,
                       semester_number, study_year, enrollment_id, graduation_year
                FROM user_profiles WHERE user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                return new UserProfile(rs.getString("full_name"), rs.getString("email_address"),
                        rs.getString("phone_number"), rs.getString("address_line"), rs.getString("city"),
                        rs.getString("state_name"), rs.getString("postal_code"),
                        rs.getString("institution_name"), rs.getString("course_name"),
                        rs.getString("department_name"), nullableInt(rs, "semester_number"),
                        nullableInt(rs, "study_year"), rs.getString("enrollment_id"),
                        nullableInt(rs, "graduation_year"));
            }
        }
    }

    public boolean isComplete(Connection connection, long userId) throws SQLException {
        UserProfile profile = find(connection, userId);
        return profile != null && profile.isComplete();
    }

    public void save(Connection connection, long userId, UserProfile profile) throws SQLException {
        connection.setAutoCommit(false);
        try {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE user_profiles SET full_name=?, email_address=?, phone_number=?, address_line=?,
                        city=?, state_name=?, postal_code=?, institution_name=?, course_name=?, department_name=?,
                        semester_number=?, study_year=?, enrollment_id=?, graduation_year=?, updated_at=SYSTIMESTAMP
                    WHERE user_id=?
                    """)) {
                bindProfile(statement, profile, 1);
                statement.setLong(15, userId);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO user_profiles
                            (user_id, full_name, email_address, phone_number, address_line, city, state_name,
                             postal_code, institution_name, course_name, department_name, semester_number,
                             study_year, enrollment_id, graduation_year)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, userId);
                    bindProfile(statement, profile, 2);
                    if (statement.executeUpdate() != 1) throw new SQLException("Profile insert did not affect one row.");
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            try { connection.rollback(); } catch (SQLException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
            throw exception;
        }
    }

    private void bindProfile(PreparedStatement statement, UserProfile profile, int start) throws SQLException {
        setString(statement, start, profile.fullName());
        setString(statement, start + 1, profile.emailAddress());
        setString(statement, start + 2, profile.phoneNumber());
        setString(statement, start + 3, profile.addressLine());
        setString(statement, start + 4, profile.city());
        setString(statement, start + 5, profile.stateName());
        setString(statement, start + 6, profile.postalCode());
        setString(statement, start + 7, profile.institutionName());
        setString(statement, start + 8, profile.courseName());
        setString(statement, start + 9, profile.departmentName());
        setInteger(statement, start + 10, profile.semesterNumber());
        setInteger(statement, start + 11, profile.studyYear());
        setString(statement, start + 12, profile.enrollmentId());
        setInteger(statement, start + 13, profile.graduationYear());
    }

    private void setString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR); else statement.setString(index, value);
    }

    private void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER); else statement.setInt(index, value);
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
