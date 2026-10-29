# MessAudit — Hostel Mess Procurement, Inventory, Wastage & Vendor Audit System

MessAudit is a Java-based web application designed to support hostel mess procurement and operational management. It brings purchase orders, goods receipts, inventory tracking, food wastage records, and vendor audits into one application backed by an Oracle database.

The project was developed as an MCA Semester 1 project to demonstrate practical skills in Java web development, database design, transaction management, authentication, and application security.

## Features

- **Dashboard:** View operational information using database-backed metrics and charts.
- **Procurement Management:** Create purchase orders and manage ordered quantities.
- **Goods Receipt Management:** Record received, accepted, and rejected quantities with transactional inventory updates.
- **Inventory Management:** View stock balances, inventory transactions, and filtered ledger records.
- **Food Wastage Tracking:** Record daily meals served, prepared quantities, and leftover measurements.
- **Vendor Audit:** Compare ordered and received quantities to support vendor performance review.
- **Reference Data Management:** Manage vendors, ingredients, and mess blocks with dependency checks.
- **Admin Data Management:** Manage eligible operational records and account roles/status with safeguards.
- **User Authentication:** Secure sign-in, read-only public registration, role-based authorization, and profile management.
- **Application Security:** Argon2id password hashing, CSRF protection, session management, login rate limiting, and parameterized SQL.
- **Database Status:** Check Oracle connectivity through a restricted status page.
- **Responsive UI:** Shared navigation, a consistent visual theme, and locally hosted JavaScript and CSS.
- **Automated Tests:** Unit tests for validation, authentication-related logic, CSRF handling, and authorization policies.

## Technology Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Web layer | Jakarta Servlets and JSP |
| Database | Oracle FreeSQL |
| Database access | JDBC |
| Build | Maven |
| Application server | Apache Tomcat 11 |
| Frontend | HTML, CSS, JavaScript, JSP |
| Password hashing | Argon2id |
| Testing | JUnit |

## Project Structure

```text
hostel-mess-audit-system/
├── database/
│   ├── 00_privilege_check.sql
│   ├── 01_create_tables.sql
│   ├── 02_add_constraints.sql
│   ├── 03_seed_synthetic_data.sql
│   ├── 04_smoke_queries.sql
│   ├── 05_verify_seed_data.sql
│   ├── 06_authentication_schema_proposal.sql
│   └── 07_user_profiles.sql
├── jdbc-test/
│   └── OracleJdbcConnectionTest.java
├── web-app/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   └── webapp/
│       └── test/
└── SECURITY_SETUP.md
```

## Prerequisites

Install and configure:

- JDK 17
- Apache Tomcat 11
- Maven, or an IDE with Maven integration
- An Oracle database/schema with the required privileges
- Database connection settings supplied through environment variables

## Database Configuration

The application reads Oracle connection settings from environment variables:

- `FREESQL_JDBC_URL`
- `FREESQL_DB_USER`
- `FREESQL_DB_PASSWORD`

Configure these variables in your local development environment or Tomcat launch configuration. Never commit real credentials, passwords, or generated password hashes to Git.

Review the SQL scripts in `database/` before running them. Apply the base schema, constraints, and synthetic seed data as appropriate for your database. Review the authentication schema proposal and apply it only if the required account tables do not already exist. Apply `07_user_profiles.sql` after the authentication schema when the profile table is needed.

Oracle schema changes should be reviewed before execution. Do not blindly rerun migrations that may already have been applied.

For administrator provisioning, session-cookie configuration, and security verification instructions, see [`SECURITY_SETUP.md`](SECURITY_SETUP.md).

## Build and Run

1. Clone the repository:

   ```bash
   git clone https://github.com/shivkcodes/hostel-mess-audit-system.git
   cd hostel-mess-audit-system
   ```

2. Configure the Oracle environment variables and prepare the database schema.

3. Open the `web-app` directory:

   ```bash
   cd web-app
   ```

4. Build the application and run unit tests:

   ```bash
   mvn clean package
   ```

5. Deploy the generated WAR from `target/` to a compatible Tomcat 11 server.

6. Configure the required environment variables in Tomcat and start the server.

The application requires a correctly configured Oracle schema and a provisioned administrator account for administrative workflows. Build and unit tests alone do not verify live database connectivity or deployment behavior.

## Security Notes

- Public registration creates `READ_ONLY` accounts; it does not create administrators.
- Administrative operations require authorization and CSRF validation.
- Passwords are stored as Argon2id hashes, not plaintext.
- Database credentials must remain outside source control.
- Operational history and inventory records have safeguards against unsafe deletion.
- Review the security setup and verification matrix before deployment.

## Testing

Run the automated test suite from `web-app`:

```bash
mvn test
```

Unit tests cover selected validation, security, and authorization behavior. They do not replace integration testing against the configured Oracle database and a running Tomcat server.

## Current Project Status

The initial source code has been published to GitHub. Database integration and deployment should be verified in the target environment before describing the application as production-ready.

## Future Improvements

- Add screenshots and a guided demo.
- Add integration tests against a dedicated test database.
- Improve deployment automation and environment-specific configuration.
- Expand reporting and operational analytics.
- Add a documented deployment and troubleshooting guide.

## Author

**Shivkcodes**

GitHub: [@shivkcodes](https://github.com/shivkcodes)

## License

No license has been specified yet. Unless a license is added, the project remains subject to the default copyright rules. Add an appropriate license before explicitly permitting reuse or redistribution.
