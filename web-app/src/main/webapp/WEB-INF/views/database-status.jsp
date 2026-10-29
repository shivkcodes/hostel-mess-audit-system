<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Database Status | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>

    <main class="page-shell" data-health-status="<c:out value='${healthStatusCode}' />" data-health-checked-at="<c:out value='${healthCheckedAtIso}' />">
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Connection diagnostics</p>
                <h1 id="page-title">Oracle Database Status</h1>
                <p class="intro">A fresh, read-only JDBC check is run for each page request. No result is cached.</p>
            </div>
        </section>

        <section class="panel health-status-card" aria-labelledby="status-title">
            <div class="health-status-heading">
                <div>
                    <p class="eyebrow">Latest check</p>
                    <h2 id="status-title">Connection health</h2>
                </div>
                <c:choose>
                    <c:when test="${connected}"><span class="health-status-badge health-status-connected">CONNECTED</span></c:when>
                    <c:otherwise><span class="health-status-badge health-status-unavailable">UNAVAILABLE</span></c:otherwise>
                </c:choose>
            </div>
            <p class="health-status-message" role="status"><c:out value="${statusMessage}" /></p>
            <p class="health-last-checked"><strong>Last checked:</strong> <c:out value="${lastCheckedAt}" /> <span>(Tomcat server local time)</span></p>
            <div class="health-action-row">
                <form id="health-check-form" action="${pageContext.request.contextPath}/database-status/check" method="get">
                    <button id="health-check-button" class="button button-primary" type="submit">
                        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M20 11a8 8 0 0 0-14.8-3M4 4v4h4M4 13a8 8 0 0 0 14.8 3M20 20v-4h-4" /></svg>
                        <c:choose><c:when test="${connected}"><span>Check again</span></c:when><c:otherwise><span>Retry connection</span></c:otherwise></c:choose>
                    </button>
                </form>
                <p id="health-check-loading" class="health-check-loading" role="status" aria-live="polite" hidden>Checking the Oracle connection…</p>
            </div>
            <p class="panel-note">This check opens a new connection and runs the fixed query <code>SELECT 1 FROM DUAL</code>. A successful result is reported only when the query returns the expected value.</p>
        </section>

        <section class="metric-grid health-metric-grid" aria-label="Health-check measurements">
            <article class="metric-card">
                <p class="metric-label">Connection time</p>
                <p class="metric-value"><c:choose><c:when test="${connectionTimeMs ne null}"><fmt:formatNumber value="${connectionTimeMs}" minFractionDigits="3" maxFractionDigits="6" groupingUsed="true" /> <span class="health-unit">ms</span></c:when><c:otherwise>—</c:otherwise></c:choose></p>
                <p class="metric-foot">Time inside the existing connection factory. On failure, this is the time until the attempt failed.</p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Query time</p>
                <p class="metric-value"><c:choose><c:when test="${queryTimeMs ne null}"><fmt:formatNumber value="${queryTimeMs}" minFractionDigits="3" maxFractionDigits="6" groupingUsed="true" /> <span class="health-unit">ms</span></c:when><c:otherwise>—</c:otherwise></c:choose></p>
                <p class="metric-foot">Statement creation, execution, result retrieval, and statement/result cleanup.</p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Total check</p>
                <p class="metric-value"><fmt:formatNumber value="${totalTimeMs}" minFractionDigits="3" maxFractionDigits="6" groupingUsed="true" /> <span class="health-unit">ms</span></p>
                <p class="metric-foot">Connection attempt through JDBC resource cleanup; excludes diagnostic logging and page rendering.</p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Health query</p>
                <p class="metric-value health-query-value"><c:choose><c:when test="${queryTimeMs eq null}">Not run</c:when><c:when test="${querySucceeded}">Succeeded</c:when><c:otherwise>Failed</c:otherwise></c:choose></p>
                <p class="metric-foot"><c:choose><c:when test="${not empty queryResult}"><code><c:out value="${queryResult}" /></code></c:when><c:when test="${queryTimeMs eq null}">No query result was available.</c:when><c:otherwise>The expected result was not returned.</c:otherwise></c:choose></p>
            </article>
        </section>

        <section class="panel health-measurement-note" aria-labelledby="measurement-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Measurement notes</p><h2 id="measurement-title">How to interpret the timings</h2></div>
            </div>
            <ul>
                <li><strong>Connection time</strong> measures the call to the configured Oracle connection factory, including driver selection and connection establishment.</li>
                <li><strong>Query time</strong> measures the fixed health query from statement creation through result retrieval and statement/result cleanup.</li>
                <li><strong>Total check</strong> measures from before connection establishment through connection cleanup. All durations use <code>System.nanoTime()</code>, are clamped to non-negative values, and are shown in milliseconds with up to six decimal places.</li>
                <li>Failures display a safe summary here. Sanitized exception details remain in the Tomcat server log.</li>
            </ul>
        </section>

        <footer class="page-footer">
            <p>This page performs a fresh read-only connectivity check and stores no history.</p>
            <p>MessAudit · Procurement &amp; Operations Management</p>
        </footer>
    </main>

    <script>
        (() => {
            const form = document.getElementById("health-check-form");
            const button = document.getElementById("health-check-button");
            const loading = document.getElementById("health-check-loading");
            const buttonLabel = button.querySelector("span").textContent;

            form.addEventListener("submit", () => {
                button.disabled = true;
                button.querySelector("span").textContent = "Checking…";
                form.setAttribute("aria-busy", "true");
                loading.hidden = false;
            });

            window.addEventListener("pageshow", () => {
                button.disabled = false;
                button.querySelector("span").textContent = buttonLabel;
                form.removeAttribute("aria-busy");
                loading.hidden = true;
            });
        })();
    </script>
</body>
</html>
