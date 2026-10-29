<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Create account | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
    <script src="${pageContext.request.contextPath}/js/auth.js?v=20261028-public-m17" defer></script>
</head>
<body class="auth-body">
    <main class="auth-shell">
        <a class="brand auth-brand" href="${pageContext.request.contextPath}/" aria-label="MessAudit home">
            <span class="brand-mark" aria-hidden="true">M</span>
            <span class="brand-copy"><span class="brand-title">MessAudit</span><span class="brand-subtitle">Procurement &amp; Operations Management</span></span>
        </a>
        <nav class="auth-nav" aria-label="Account navigation"><a href="${pageContext.request.contextPath}/login">Sign in</a><a href="${pageContext.request.contextPath}/register" aria-current="page">Register</a></nav>
        <section class="panel auth-panel" aria-labelledby="signup-title">
            <p class="eyebrow">Create a MessAudit account</p>
            <h1 id="signup-title">Create your account</h1>
            <p class="intro auth-intro">Set up your account to view operational records in the MessAudit workspace.</p>
            <c:if test="${not empty signupError}"><div class="notice notice-error" role="alert"><c:out value="${signupError}" /></div></c:if>
            <form class="auth-form" action="${pageContext.request.contextPath}/register" method="post" accept-charset="UTF-8">
                <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />">
                <div class="form-field">
                    <label for="username">Username</label>
                    <input id="username" name="username" type="text" autocomplete="username" minlength="3" maxlength="64" pattern="[a-zA-Z0-9][a-zA-Z0-9._@-]{2,63}" aria-describedby="username-help" required>
                    <small id="username-help">3–64 characters; letters, numbers, dot, underscore, @, and hyphen.</small>
                </div>
                <div class="form-field">
                    <label for="password">Password</label>
                    <div class="password-control"><input id="password" name="password" type="password" autocomplete="new-password" minlength="12" maxlength="128" required><button class="password-toggle" type="button" data-password-toggle="password" aria-pressed="false">Show</button></div>
                    <small>Use 12–128 characters. Passwords are stored as Argon2id hashes.</small>
                </div>
                <div class="form-field">
                    <label for="password-confirmation">Confirm password</label>
                    <div class="password-control"><input id="password-confirmation" name="passwordConfirmation" type="password" autocomplete="new-password" minlength="12" maxlength="128" required><button class="password-toggle" type="button" data-password-toggle="password-confirmation" aria-pressed="false">Show</button></div>
                </div>
                <button class="button button-primary auth-submit" type="submit" data-submit-button>Create account</button>
            </form>
            <p class="auth-footnote">Already have an account? <a href="${pageContext.request.contextPath}/login">Sign in</a>.</p>
            <p class="auth-privacy-note"><span aria-hidden="true">▣</span> New accounts are READ_ONLY. Only an administrator can grant management access.</p>
            <a class="auth-home-link" href="${pageContext.request.contextPath}/">← Back to MessAudit home</a>
        </section>
    </main>
</body>
</html>
