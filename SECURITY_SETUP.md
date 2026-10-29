# Authentication and Authorization Setup

## Scope and account model

Application accounts are stored in `APP_USERS`; Oracle credentials remain in the existing Tomcat environment variables and are separate from application accounts. Public sign-up creates an active `READ_ONLY` account only. There is no default username/password and self-registration cannot create an administrator.

- `ADMIN` can use the existing procurement and goods-receipt POST workflows and open Database Status.
- `READ_ONLY` can view the operational pages but cannot submit state-changing requests or open Database Status.
- Both roles can update only their own profile at `/profile`; the route is session-protected and CSRF-validated.
- The public landing page, sign-in, read-only sign-up, and local CSS/JavaScript assets are public.
- Vendors, ingredients, and mess blocks have ADMIN-only create, edit, activate/deactivate, and dependency-checked delete operations. Unknown POST routes are rejected by the security filter.
- `/admin/data-management` and `/admin/data-cleanup` are ADMIN-only. Management includes constrained daily-record and account-role/status actions plus read-only views of inventory ledgers, contracts, penalty reviews, and audit events.
- Cleanup is record-by-record. The schema has no reliable marker distinguishing existing demo rows from legitimate operational records, so the application does not offer a bulk demo-data purge.

## Oracle schema setup

The application never applies DDL. The new profile page requires `USER_PROFILES`, introduced by `database/07_user_profiles.sql`. Run it once, after `database/06_authentication_schema_proposal.sql`, in the intended Oracle FreeSQL schema using the existing VS Code connection. It creates a separate profile table with a primary key and a non-cascading foreign key to `APP_USERS`; it does not change existing account roles or authentication fields. Before deployment, confirm `APP_USERS`, `AUDIT_LOG`, and the operational tables exist. Oracle commits DDL implicitly, so if execution stops partway through, inspect `USER_TABLES` and `USER_CONSTRAINTS` before deciding how to continue; do not rerun blindly.

## First-time setup and account access

The build does not verify or change the live FreeSQL schema. If the required account table is absent, login and sign-up cannot look up or create accounts and the page reports a generic temporary-unavailability message. Confirm the schema setup before deployment.

After the migrations and app deployment, a user can open `/signup`, choose their own username and password (minimum 12 characters), and sign in with those values. New accounts remain `READ_ONLY`; the first sign-in directs an incomplete profile to `/profile`. Existing users without a completed profile can still sign in and complete it. The profile stores name, email, academic program, and optional contact/student details separately from authentication data. The app does not issue default credentials or recover a forgotten password. Self-registered accounts have `READ_ONLY` access to operational data, so only intended users should be given the URL. An administrator account is provisioned separately by the owner.

## Provision the first administrator

1. Build the project so `web-app/target/classes` contains the compiled helper.
2. From a local terminal with JDK 17, run `java -cp "web-app\target\classes;$env:USERPROFILE\.m2\repository\org\bouncycastle\bcprov-jdk18on\1.86\bcprov-jdk18on-1.86.jar" edu.hostel.mess.web.security.PasswordHashGenerator` from the project root. It reads and confirms the password without echoing it and prints only a versioned Argon2id password hash. It requires an interactive PowerShell terminal; do not pass a password as an argument or put it in a script. Handle the generated hash privately; it is a credential verifier.
3. Execute a local SQL insert with a chosen lowercase username and the generated hash:

   ```sql
   INSERT INTO app_users (username, password_hash, role_code)
   VALUES ('<chosen-lowercase-username>', '<locally-generated-hash>', 'ADMIN');
   COMMIT;
   ```

   Replace both placeholders before execution. Do not save a real hash in a tracked SQL file. Create later restricted accounts with `READ_ONLY` only when needed. The hash is still sensitive and should be handled privately.

## Tomcat session-cookie configuration

`HOSTEL_SESSION_COOKIE_SECURE` defaults to `true`. Keep it true when the application is served over HTTPS. For local testing at `http://localhost:8080`, set it to `false` in the Eclipse Tomcat server's launch environment; otherwise browsers correctly withhold the secure session cookie over HTTP. The listener always sets `HttpOnly`, `SameSite=Lax`, a 15-minute server-side session timeout, and cookie-only session tracking. CSRF tokens are still required independently of SameSite.

## Verification matrix

Run these checks after confirming the schema, provisioning an account, setting the Tomcat environment, and deploying the WAR. These are verification steps, not results already observed by Codex.

| Case | Expected result |
|---|---|
| Anonymous GET `/dashboard` or a module route | Redirect to `/login`; no redirect loop |
| Valid ADMIN login | Redirect to Dashboard; session ID changes; CSRF token rotates |
| Wrong password, unknown username, or inactive account | Same generic credential message; no secret in page or server logs |
| Five failed attempts for one client/username pair or client address | Subsequent login attempt is throttled for the 15-minute fixed window; limiter is per Tomcat process |
| Five registration submissions from one client address | Subsequent registration attempt is throttled for the 15-minute fixed window; limiter is per Tomcat process |
| POST `/signup` without or with an altered `_csrf` token | 403; no account is created |
| READ_ONLY login | Read-only pages work; Database Status and state-changing POSTs return 403 |
| Anonymous POST to `/procurement` or `/goods-receipts` | 401; no database write |
| ADMIN POST without or with an altered `_csrf` token | 403; no database write |
| ADMIN POST with the current session token | Existing validation and transaction workflow runs; verify resulting database state |
| POST `/logout` with current token | Redirect to sign-in; old session cannot access protected routes |
| Reuse a session after 15 minutes idle | Session is expired and protected GET redirects to sign-in |
| Password storage/log review | `APP_USERS.PASSWORD_HASH` contains the versioned Argon2id encoding; plaintext is absent from database and logs |

The login limiter is intentionally in-memory and single-instance. It resets when Tomcat restarts and does not coordinate across multiple Tomcat nodes; this suits the current single-instance deployment.

## Admin data management and cleanup

- `/admin/data-management` and `/admin/data-cleanup` require an authenticated `ADMIN` session. Mutations use POST, session-bound CSRF validation, server-side role checks, parameterized SQL, validation, and audit records.
- Reference-data deletion is blocked when dependency checks find historical or operational references; where the schema supports it, deactivation is available instead. Ingredient base units cannot be changed after references exist.
- Purchase orders can be deleted only when receipt, review, and audit history is absent; open or partial orders can instead be cancelled with a reason.
- A receipt can be removed only while unverified/unprocessed and without ledger or audit references. A verified receipt remains as evidence; if accepted inventory was posted, the cleanup action can add a compensating `ADJUSTMENT_OUT` ledger entry only when stock is sufficient. Original receipt and ledger rows are retained.
- Inventory balances and transaction history are displayed read-only. Daily mess records can be created, edited, or explicitly deleted while preserving NULL measurements.
- Account management changes only role/status, does not display password hashes, prevents changes to the current admin account, and protects the last active administrator.
- Account role/status changes require explicit confirmation. Account hard deletion is not offered; deactivation preserves historical audit attribution.
- Profiles are updated only for the authenticated user ID. Profile values and enrollment identifiers are not included in audit payloads or server logs. Profile completion is derived from name, email, institution, course, and semester.
- The admin header database indicator uses a real read-only Oracle health check and caches the sanitized result in the browser session for at most 60 seconds. `/database-status/indicator` remains ADMIN-only and never returns connection details.
- There is no safe bulk cleanup for existing demo data because the schema does not distinguish demo rows from legitimate records. Select and review eligible records individually; do not infer that a row is demo data from its name or reference.

## Automated unit tests

Run `mvn clean package` from `web-app` (or Eclipse: **Run As → Maven build…**, goal `clean package`). The Maven test phase runs unit tests for profile and daily-record validation, reference-data validation, password encoding/salt behavior, the login rate limiter, CSRF token comparison, and route authorization policy. These tests do not connect to Oracle or start Tomcat; the integration cases in the matrix above still require local deployment and an available, correctly set up Oracle schema.
