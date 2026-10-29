import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Minimal Oracle JDBC Thin connectivity check for Java 17.
 *
 * Required environment variables:
 *   FREESQL_JDBC_URL
 *   FREESQL_DB_USER
 *   FREESQL_DB_PASSWORD
 */
public final class OracleJdbcConnectionTest {
    private static final String QUERY = "SELECT 1 FROM DUAL";

    private OracleJdbcConnectionTest() {
    }

    public static void main(String[] args) {
        int exitCode = runConnectivityCheck();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int runConnectivityCheck() {
        String jdbcUrl = requiredEnvironmentVariable("FREESQL_JDBC_URL");
        String username = requiredEnvironmentVariable("FREESQL_DB_USER");
        String password = requiredEnvironmentVariable("FREESQL_DB_PASSWORD");

        if (jdbcUrl == null || username == null || password == null) {
            System.err.println("CONNECTION_FAILED: Configure the missing environment variable(s) locally in Eclipse.");
            return 2;
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(QUERY);
             ResultSet resultSet = statement.executeQuery()) {

            if (!resultSet.next()) {
                System.err.println("CONNECTION_FAILED: SELECT 1 FROM DUAL returned no rows.");
                return 1;
            }

            int value = resultSet.getInt(1);
            if (value != 1) {
                System.err.println("CONNECTION_FAILED: SELECT 1 FROM DUAL returned an unexpected value.");
                return 1;
            }

            System.out.println("SELECT_RESULT=" + value);
            System.out.println("CONNECTION_OK: Oracle JDBC Thin connected and the query succeeded.");
            return 0;
        } catch (SQLException exception) {
            System.err.println("CONNECTION_FAILED: Oracle JDBC could not complete the connection/query.");
            printSqlDiagnostics(exception);
            return 1;
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            System.err.println("Missing or empty environment variable: " + name);
            return null;
        }
        return value;
    }

    private static void printSqlDiagnostics(SQLException exception) {
        SQLException current = exception;
        int diagnosticNumber = 1;

        while (current != null) {
            String sqlState = current.getSQLState();
            int errorCode = current.getErrorCode();

            System.err.println("Diagnostic " + diagnosticNumber + ": "
                    + current.getClass().getSimpleName());
            System.err.println("  SQLState: " + (sqlState == null ? "unavailable" : sqlState));
            System.err.println("  Vendor error code: " + errorCode);
            System.err.println("  Hint: " + diagnosticHint(sqlState, errorCode));

            current = current.getNextException();
            diagnosticNumber++;
        }
    }

    private static String diagnosticHint(String sqlState, int errorCode) {
        if (errorCode == 1017 || (sqlState != null && sqlState.startsWith("28"))) {
            return "Check the locally configured FreeSQL username and password.";
        }
        if (errorCode == 12514 || errorCode == 12505) {
            return "Check the host, port, and service name from FreeSQL Schema Connection Details.";
        }
        if (errorCode == 17002 || (sqlState != null && sqlState.startsWith("08"))) {
            return "Check the endpoint, network access, and any local firewall restrictions.";
        }
        if (sqlState != null && sqlState.startsWith("42")) {
            return "Check the SQL statement and database compatibility.";
        }
        if (errorCode == 0 && sqlState == null) {
            return "Check that ojdbc17.jar is attached to the Eclipse project's Build Path.";
        }
        return "Use the SQLState and vendor error code to identify the Oracle JDBC failure.";
    }
}
