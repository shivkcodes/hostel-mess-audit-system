<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sign in | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
    <script src="${pageContext.request.contextPath}/js/auth.js?v=20261028-public-m17" defer></script>
</head>
<body class="auth-body">
    <main class="auth-shell">
        <a class="brand auth-brand" href="${pageContext.request.contextPath}/" aria-label="MessAudit home">
            <span class="brand-mark" aria-hidden="true">M</span>
            <span class="brand-copy"><span class="brand-title">MessAudit</span><span class="brand-subtitle">Procurement &amp; Operations Management</span></span>
        </a>
        <nav class="auth-nav" aria-label="Account navigation"><a href="${pageContext.request.contextPath}/login" aria-current="page">Sign in</a><a href="${pageContext.request.contextPath}/register">Register</a></nav>
        <section class="panel auth-panel" aria-labelledby="login-title">
            <p class="eyebrow">Secure workspace</p>
            <h1 id="login-title">Welcome back</h1>
            <p class="intro auth-intro">Sign in to review procurement, stock, meal measurements, and vendor delivery records.</p>
            <c:if test="${loggedOut}"><div class="notice notice-success" role="status">You have been signed out.</div></c:if>
            <c:if test="${registered}"><div class="notice notice-success" role="status">Account created. Sign in with the username and password you chose; you will then complete your profile.</div></c:if>
            <c:if test="${not empty loginError}"><div class="notice notice-error" role="alert"><c:out value="${loginError}" /></div></c:if>
            <form class="auth-form" action="${pageContext.request.contextPath}/login" method="post" accept-charset="UTF-8">
                <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />">
                <c:if test="${not empty returnTo}"><input type="hidden" name="returnTo" value="<c:out value='${returnTo}' />"></c:if>
                <div class="form-field">
                    <label for="username">Username</label>
                    <input id="username" name="username" type="text" autocomplete="username" maxlength="64" required>
                </div>
                <div class="form-field">
                    <label for="password">Password</label>
                    <div class="password-control"><input id="password" name="password" type="password" autocomplete="current-password" maxlength="128" required><button class="password-toggle" type="button" data-password-toggle="password" aria-pressed="false">Show</button></div>
                </div>
                <button class="button button-primary auth-submit" type="submit" data-submit-button>Sign in</button>
            </form>
            <p class="auth-footnote">New to MessAudit? <a href="${pageContext.request.contextPath}/register">Create a read-only account</a>. Admin access is assigned separately.</p>
            <p class="auth-privacy-note"><span aria-hidden="true">▣</span> Your password is verified securely and is never shown in operational records.</p>
            <a class="auth-home-link" href="${pageContext.request.contextPath}/">← Back to MessAudit home</a>
        </section>
    </main>
</body>
</html>
