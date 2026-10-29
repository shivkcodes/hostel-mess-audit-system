<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${pageTitle}" /></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>

    <main class="page-shell">
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Hostel operations</p>
                <h1 id="page-title"><c:out value="${pageTitle}" /></h1>
            </div>
        </section>
        <section class="panel home-panel">
            <p class="intro"><c:out value="${message}" /></p>
            <div class="form-actions">
                <a class="button button-primary" href="${pageContext.request.contextPath}/dashboard">Open dashboard</a>
            </div>
        </section>
        <footer class="page-footer">
            <p>MessAudit · Procurement &amp; Operations Management</p>
        </footer>
    </main>
</body>
</html>
