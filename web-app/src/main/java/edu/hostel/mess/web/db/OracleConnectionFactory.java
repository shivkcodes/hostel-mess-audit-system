package edu.hostel.mess.web.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens Oracle JDBC connections using settings supplied to the Tomcat process.
 */
public final class OracleConnectionFactory {
    private static final String JDBC_URL_ENV = "FREESQL_JDBC_URL";
    private static final String DB_USER_ENV = "FREESQL_DB_USER";
    private static final String DB_PASSWORD_ENV = "FREESQL_DB_PASSWORD";
    private static final String ORACLE_DRIVER_CLASS = "oracle.jdbc.OracleDriver";

    private OracleConnectionFactory() {
    }

    public static Connection openConnection() throws SQLException {
        String jdbcUrl = requireEnvironmentVariable(JDBC_URL_ENV);
        String username = requireEnvironmentVariable(DB_USER_ENV);
        String password = requireEnvironmentVariable(DB_PASSWORD_ENV);

        // Load from the web application's classloader before asking DriverManager to select a driver.
        try {
            Class.forName(ORACLE_DRIVER_CLASS);
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Oracle JDBC driver is unavailable on the web application classpath.", exception);
        }

        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new MissingDatabaseConfigurationException(name);
        }
        return value;
    }

    public static final class MissingDatabaseConfigurationException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private MissingDatabaseConfigurationException(String variableName) {
            super("Required database environment variable is missing: " + variableName);
        }
    }
}
