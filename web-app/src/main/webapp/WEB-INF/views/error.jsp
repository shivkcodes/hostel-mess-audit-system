<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Request unavailable | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body class="auth-body">
    <main class="auth-shell">
        <section class="panel auth-panel" aria-labelledby="error-title">
            <p class="eyebrow">Hostel Mess workspace</p>
            <h1 id="error-title">We couldn't complete that request</h1>
            <p class="intro auth-intro">Return to the dashboard and try again. If your session has expired, sign in again.</p>
            <div class="auth-actions">
                <a class="button button-primary" href="${pageContext.request.contextPath}/login">Sign in</a>
                <a class="button button-secondary" href="${pageContext.request.contextPath}/dashboard">Dashboard</a>
            </div>
        </section>
    </main>
</body>
</html>
