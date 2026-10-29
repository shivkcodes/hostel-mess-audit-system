<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Vendor Audit | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>


    <main id="vendor-audit-main" class="page-shell">
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Read-only procurement review</p>
                <h1 id="page-title">Vendor Audit &amp; Performance</h1>
                <p class="intro">Compare ordered quantities with verified receipt evidence. Short quantities show undelivered balance to date; they do not establish vendor fault.</p>
            </div>
            <c:choose>
                <c:when test="${vendorAuditAvailable}"><p class="data-note"><span class="live-dot" aria-hidden="true"></span> Live database figures</p></c:when>
                <c:otherwise><p class="data-note"><span class="offline-dot" aria-hidden="true"></span> Database unavailable</p></c:otherwise>
            </c:choose>
        </section>

        <c:if test="${not vendorAuditAvailable}">
            <div class="notice notice-error" role="alert"><c:out value="${vendorAuditError}" /></div>
        </c:if>
        <c:if test="${not empty filterErrors}">
            <div class="notice notice-error" role="alert" aria-labelledby="filter-errors-title">
                <h2 id="filter-errors-title">Some filters need attention</h2>
                <ul><c:forEach var="error" items="${filterErrors}"><li><c:out value="${error}" /></li></c:forEach></ul>
            </div>
        </c:if>

        <section class="panel audit-filter-panel" aria-labelledby="filter-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Report scope</p><h2 id="filter-title">Filters</h2></div>
                <span class="unit-tag">Read only</span>
            </div>
            <form id="vendor-audit-filter-form" action="${pageContext.request.contextPath}/vendor-audit" method="get">
                <div class="audit-filter-grid">
                    <div class="form-field">
                        <label for="filter-vendor">Vendor</label>
                        <select id="filter-vendor" name="vendorId">
                            <option value="">All vendors</option>
                            <c:forEach var="vendor" items="${vendors}">
                                <c:choose>
                                    <c:when test="${selectedVendorId eq vendor.vendorId}"><option value="${vendor.vendorId}" selected="selected"><c:out value="${vendor.vendorName}" /> (<c:out value="${vendor.vendorCode}" />)</option></c:when>
                                    <c:otherwise><option value="${vendor.vendorId}"><c:out value="${vendor.vendorName}" /> (<c:out value="${vendor.vendorCode}" />)</option></c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="form-field">
                        <label for="filter-from">Order date from</label>
                        <input id="filter-from" name="fromDate" type="date" value="<c:out value='${selectedFromDate}' />">
                    </div>
                    <div class="form-field">
                        <label for="filter-to">Order date through</label>
                        <input id="filter-to" name="toDate" type="date" value="<c:out value='${selectedToDate}' />">
                    </div>
                    <div class="audit-filter-actions">
                        <button class="button button-primary" type="submit" <c:if test="${not vendorAuditAvailable}">disabled</c:if>>Apply filters</button>
                        <a class="button button-secondary" href="${pageContext.request.contextPath}/vendor-audit">Clear filters</a>
                    </div>
                </div>
            </form>
            <p id="vendor-audit-loading" class="inventory-loading" role="status" aria-live="polite" hidden>Refreshing vendor audit data…</p>
            <p class="panel-note">Date filters select purchase orders by their order date. Verified receipts for those orders are counted to date, including receipts recorded after the selected date range.</p>
        </section>

        <section class="metric-grid audit-metric-grid" aria-label="Vendor audit summary">
            <article class="metric-card"><p class="metric-label">Vendors in view</p><p class="metric-value"><c:out value="${summary.vendorCount}" /></p><p class="metric-foot">Matching the current vendor filter</p></article>
            <article class="metric-card"><p class="metric-label">Purchase orders</p><p class="metric-value"><c:out value="${summary.orderCount}" /></p><p class="metric-foot">Includes each recorded order status</p></article>
            <article class="metric-card"><p class="metric-label">Open / partial orders</p><p class="metric-value"><c:out value="${summary.openOrderCount}" /> / <c:out value="${summary.partialOrderCount}" /></p><p class="metric-foot">Current database order states</p></article>
            <article class="metric-card"><p class="metric-label">Verified / pending receipts</p><p class="metric-value"><c:out value="${summary.verifiedReceiptCount}" /> / <c:out value="${summary.unverifiedReceiptCount}" /></p><p class="metric-foot">Pending receipts are excluded from quantity totals</p></article>
        </section>

        <section class="panel vendor-audit-panel" aria-labelledby="vendors-title">
            <div class="panel-heading"><div><p class="eyebrow">Vendor roster</p><h2 id="vendors-title">Vendor order and receipt overview</h2></div></div>
            <p class="panel-note">Order and receipt counts include all statuses. Quantity totals below exclude cancelled purchase orders and use verified receipt items only.</p>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Vendor performance overview; scroll horizontally to see all columns">
                <table class="vendor-summary-table">
                    <caption class="visually-hidden">Vendor order states, receipt verification, and links to detail</caption>
                    <thead><tr><th scope="col">Vendor</th><th scope="col">Status</th><th scope="col">Orders</th><th scope="col">Open</th><th scope="col">Partial</th><th scope="col">Closed</th><th scope="col">Cancelled</th><th scope="col">Verified receipts</th><th scope="col">Pending verification</th><th scope="col">Detail</th></tr></thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not vendorAuditAvailable}"><tr><td colspan="10" class="empty-state">Vendor data is unavailable right now.</td></tr></c:when>
                            <c:when test="${empty vendorSummaries}"><tr><td colspan="10" class="empty-state">No vendors match these filters.</td></tr></c:when>
                            <c:otherwise><c:forEach var="row" items="${vendorSummaries}">
                                <c:url var="detailUrl" value="/vendor-audit"><c:param name="vendorId" value="${row.vendorId}" /><c:if test="${not empty selectedFromDate}"><c:param name="fromDate" value="${selectedFromDate}" /></c:if><c:if test="${not empty selectedToDate}"><c:param name="toDate" value="${selectedToDate}" /></c:if></c:url>
                                <tr>
                                    <th scope="row"><c:out value="${row.vendorName}" /><span class="sub-cell"><c:out value="${row.vendorCode}" /></span></th>
                                    <td><c:choose><c:when test="${row.activeFlag eq 'Y'}">Active</c:when><c:otherwise>Inactive</c:otherwise></c:choose></td>
                                    <td><c:out value="${row.orderCount}" /></td><td><c:out value="${row.openOrderCount}" /></td><td><c:out value="${row.partialOrderCount}" /></td><td><c:out value="${row.closedOrderCount}" /></td><td><c:out value="${row.cancelledOrderCount}" /></td><td><c:out value="${row.verifiedReceiptCount}" /></td><td><c:out value="${row.unverifiedReceiptCount}" /></td>
                                    <td><a class="table-action" href="${detailUrl}">Review</a></td>
                                </tr>
                            </c:forEach></c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="panel vendor-audit-panel" aria-labelledby="quantities-title">
            <div class="panel-heading"><div><p class="eyebrow">Verified quantities</p><h2 id="quantities-title">Procurement quantities by vendor and unit</h2></div></div>
            <p class="panel-note">Short quantity is ordered quantity minus cumulative verified received quantity, floored at zero. For open or partial orders it is outstanding quantity to date, not a fault finding. Units are kept separate.</p>
            <div class="analytics-chart" data-analytics-chart="vendor"
                 data-chart-data="<c:out value='${vendorChartJson}' />"
                 data-chart-available="${vendorAuditAvailable}">
                <div class="analytics-chart-toolbar">
                    <div class="form-field analytics-unit-field">
                        <label for="vendor-chart-unit">Ingredient unit</label>
                        <select id="vendor-chart-unit" data-chart-unit disabled><option>Chart data unavailable</option></select>
                    </div>
                    <div class="analytics-legend" data-chart-legend aria-label="Chart series"></div>
                </div>
                <p class="analytics-chart-status" data-chart-status role="status" aria-live="polite">Enable JavaScript to view the interactive chart; the verified-quantity table remains available below.</p>
                <div class="analytics-chart-viewport" data-chart-viewport tabindex="0" role="region" aria-label="Scrollable vendor quantity comparison chart"></div>
            </div>
            <p class="panel-note">For each selected unit, the chart shows at most 10 vendors ordered by ordered quantity (ties by vendor code). Received quantities include verified receipt evidence only; cancelled orders are excluded.</p>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Procurement quantity totals by vendor and unit; scroll horizontally to see all columns">
                <table class="vendor-quantity-table">
                    <caption class="visually-hidden">Ordered, verified received, accepted, rejected, and outstanding quantities for each vendor and unit</caption>
                    <thead><tr><th scope="col">Vendor</th><th scope="col">Unit</th><th scope="col">Orders</th><th scope="col">Lines</th><th scope="col">Ordered</th><th scope="col">Received</th><th scope="col">Accepted</th><th scope="col">Rejected</th><th scope="col">Short to date</th><th scope="col">Accepted share</th></tr></thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not vendorAuditAvailable}"><tr><td colspan="10" class="empty-state">Quantity data is unavailable right now.</td></tr></c:when>
                            <c:when test="${empty quantitySummaries}"><tr><td colspan="10" class="empty-state">No non-cancelled order lines match these filters.</td></tr></c:when>
                            <c:otherwise><c:forEach var="row" items="${quantitySummaries}">
                                <tr><th scope="row"><c:out value="${row.vendorName}" /><span class="sub-cell"><c:out value="${row.vendorCode}" /></span></th><td><c:out value="${row.baseUnit}" /></td><td><c:out value="${row.orderCount}" /></td><td><c:out value="${row.lineCount}" /></td>
                                    <td><fmt:formatNumber value="${row.orderedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.receivedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.acceptedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.rejectedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.shortQty}" maxFractionDigits="3" groupingUsed="true" /></td>
                                    <td><c:choose><c:when test="${empty row.acceptedSharePercent}">N/A</c:when><c:otherwise><fmt:formatNumber value="${row.acceptedSharePercent}" maxFractionDigits="2" />%</c:otherwise></c:choose></td>
                                </tr>
                            </c:forEach></c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
            <p class="panel-foot">Accepted share = accepted quantity ÷ verified received quantity × 100, only when received quantity is above zero. It is a quantity ratio, not a quality score.</p>
        </section>

        <section class="panel audit-limits" aria-labelledby="limits-title">
            <div class="panel-heading"><div><p class="eyebrow">Interpretation limits</p><h2 id="limits-title">What the stored data does not establish</h2></div></div>
            <ul><li>Payment status is unavailable because payment records are not stored.</li><li>Invoice compliance is unavailable; stored invoice amounts are not compared here because no approved matching rule is defined.</li><li>Delivery timeliness and quality scores are unavailable. The accepted share above is a quantity ratio, not a quality score.</li><li>Contract rules and penalty review records are not evaluated by this evidence page. It creates no penalty proposal or composite vendor rating.</li></ul>
        </section>

        <c:if test="${not empty selectedVendor}">
            <section class="panel vendor-audit-panel" aria-labelledby="detail-title">
                <div class="panel-heading"><div><p class="eyebrow">Evidence review</p><h2 id="detail-title">Orders and receipts for <c:out value="${selectedVendor.vendorName}" /></h2></div><span class="unit-tag"><c:out value="${selectedVendor.vendorCode}" /></span></div>
                <div class="table-wrap" role="region" tabindex="0" aria-label="Purchase order detail; scroll horizontally to see all columns">
                    <table class="vendor-order-table"><caption class="visually-hidden">Selected vendor purchase orders, dates, states, and receipt verification counts</caption>
                        <thead><tr><th scope="col">Purchase order</th><th scope="col">Ordered</th><th scope="col">Expected by</th><th scope="col">Status</th><th scope="col">Items</th><th scope="col">Receipts</th><th scope="col">Verified</th><th scope="col">Pending</th></tr></thead>
                        <tbody><c:choose><c:when test="${empty orders}"><tr><td colspan="8" class="empty-state">No purchase orders match this vendor and date range.</td></tr></c:when><c:otherwise><c:forEach var="row" items="${orders}"><tr><th scope="row"><c:out value="${row.poNumber}" /></th><td><fmt:formatDate value="${row.orderedAt}" pattern="dd MMM yyyy" /></td><td><c:choose><c:when test="${empty row.expectedBy}">Not set</c:when><c:otherwise><fmt:formatDate value="${row.expectedBy}" pattern="dd MMM yyyy" /></c:otherwise></c:choose></td><td><span class="status-pill status-${fn:toLowerCase(row.status)}"><c:out value="${row.status}" /></span></td><td><c:out value="${row.itemCount}" /></td><td><c:out value="${row.receiptCount}" /></td><td><c:out value="${row.verifiedReceiptCount}" /></td><td><c:out value="${row.unverifiedReceiptCount}" /></td></tr></c:forEach></c:otherwise></c:choose></tbody>
                    </table>
                </div>
            </section>

            <section class="panel vendor-audit-panel" aria-labelledby="lines-title">
                <div class="panel-heading"><div><p class="eyebrow">Order line evidence</p><h2 id="lines-title">Ordered and verified quantities</h2></div></div>
                <div class="table-wrap" role="region" tabindex="0" aria-label="Purchase order line quantities; scroll horizontally to see all columns">
                    <table class="vendor-line-table"><caption class="visually-hidden">Per-item ordered and cumulative verified receipt quantities</caption>
                        <thead><tr><th scope="col">Order</th><th scope="col">Ingredient</th><th scope="col">Unit</th><th scope="col">Ordered</th><th scope="col">Received</th><th scope="col">Accepted</th><th scope="col">Rejected</th><th scope="col">Short to date</th></tr></thead>
                        <tbody><c:choose><c:when test="${empty orderLines}"><tr><td colspan="8" class="empty-state">No order lines are available for this selection.</td></tr></c:when><c:otherwise><c:forEach var="row" items="${orderLines}"><tr><th scope="row"><c:out value="${row.poNumber}" /><span class="sub-cell"><c:out value="${row.status}" /></span></th><td><c:out value="${row.ingredientName}" /></td><td><c:out value="${row.baseUnit}" /></td><td><fmt:formatNumber value="${row.orderedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.receivedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.acceptedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.rejectedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><c:choose><c:when test="${row.status eq 'CANCELLED'}">N/A</c:when><c:otherwise><fmt:formatNumber value="${row.shortQty}" maxFractionDigits="3" groupingUsed="true" /></c:otherwise></c:choose></td></tr></c:forEach></c:otherwise></c:choose></tbody>
                    </table>
                </div>
            </section>

            <section class="panel vendor-audit-panel" aria-labelledby="receipts-title">
                <div class="panel-heading"><div><p class="eyebrow">Receipt trace</p><h2 id="receipts-title">Receipt verification evidence</h2></div></div>
                <p class="panel-note">Unverified receipts are listed for traceability but excluded from the quantity totals above. Multiple verified receipts are summed by purchase-order line.</p>
                <div class="table-wrap" role="region" tabindex="0" aria-label="Receipt item evidence; scroll horizontally to see all columns">
                    <table class="vendor-receipt-table"><caption class="visually-hidden">Receipt arrival, verification state, and per-item received, accepted, and rejected amounts</caption>
                        <thead><tr><th scope="col">Order</th><th scope="col">Receipt ID</th><th scope="col">Arrived</th><th scope="col">Verification</th><th scope="col">Ingredient</th><th scope="col">Received</th><th scope="col">Accepted</th><th scope="col">Rejected</th></tr></thead>
                        <tbody><c:choose><c:when test="${empty receiptEvidence}"><tr><td colspan="8" class="empty-state">No delivery receipts match this vendor and date range.</td></tr></c:when><c:otherwise><c:forEach var="row" items="${receiptEvidence}"><tr><th scope="row"><c:out value="${row.poNumber}" /></th><td><c:out value="${row.receiptId}" /></td><td><fmt:formatDate value="${row.arrivedAt}" pattern="dd MMM yyyy, HH:mm" /></td><td><c:choose><c:when test="${empty row.verifiedAt}"><span class="status-pill status-partial">Pending verification</span></c:when><c:otherwise><span class="status-pill status-closed">Verified</span><span class="sub-cell"><fmt:formatDate value="${row.verifiedAt}" pattern="dd MMM yyyy, HH:mm" /></span></c:otherwise></c:choose></td>
                            <c:choose><c:when test="${empty row.receiptItemId}"><td colspan="4">No receipt item rows</td></c:when><c:otherwise><td><c:out value="${row.ingredientName}" /><span class="sub-cell"><c:out value="${row.baseUnit}" /></span></td><td><fmt:formatNumber value="${row.receivedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.acceptedQty}" maxFractionDigits="3" groupingUsed="true" /></td><td><fmt:formatNumber value="${row.rejectedQty}" maxFractionDigits="3" groupingUsed="true" /></td></c:otherwise></c:choose>
                        </tr></c:forEach></c:otherwise></c:choose></tbody>
                    </table>
                </div>
            </section>
        </c:if>

        <footer class="page-footer"><p>This page is read-only. It presents recorded order and verified receipt evidence, not an automatic vendor judgment.</p><p>MessAudit · Procurement &amp; Operations Management</p></footer>
    </main>
    <script>
        document.getElementById("vendor-audit-filter-form").addEventListener("submit", function () {
            document.getElementById("vendor-audit-main").setAttribute("aria-busy", "true");
            document.getElementById("vendor-audit-loading").hidden = false;
        });
    </script>
    <script src="${pageContext.request.contextPath}/js/analytics-charts.js?v=20261028-12" defer></script>
</body>
</html>
