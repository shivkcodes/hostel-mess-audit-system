<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Dashboard | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>


    <main class="page-shell">
        <c:if test="${not empty profileFlashMessage}">
            <div class="notice notice-success profile-dashboard-flash" role="status"><c:out value="${profileFlashMessage}" /></div>
        </c:if>
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Operations overview</p>
                <h1 id="page-title">Dashboard</h1>
                <p class="intro">A current view of procurement records, stock thresholds, and measured mess leftovers.</p>
            </div>
            <c:choose>
                <c:when test="${dashboardAvailable}">
                    <p class="data-note"><span class="live-dot" aria-hidden="true"></span> Live database figures</p>
                </c:when>
                <c:otherwise>
                    <p class="data-note"><span class="offline-dot" aria-hidden="true"></span> Database unavailable</p>
                </c:otherwise>
            </c:choose>
        </section>

        <c:if test="${not dashboardAvailable}">
            <div class="alert" role="alert"><c:out value="${dashboardError}" /></div>
        </c:if>

        <section class="metric-grid" aria-label="Record totals">
            <article class="metric-card">
                <p class="metric-label">Total vendors</p>
                <p class="metric-value">
                    <c:choose><c:when test="${dashboardAvailable}"><fmt:formatNumber value="${vendorCount}" groupingUsed="true" /></c:when><c:otherwise>Unavailable</c:otherwise></c:choose>
                </p>
                <p class="metric-foot">Vendor records in this schema</p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Purchase orders</p>
                <p class="metric-value">
                    <c:choose><c:when test="${dashboardAvailable}"><fmt:formatNumber value="${purchaseOrderCount}" groupingUsed="true" /></c:when><c:otherwise>Unavailable</c:otherwise></c:choose>
                </p>
                <p class="metric-foot">All recorded order statuses</p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Ingredients</p>
                <p class="metric-value">
                    <c:choose><c:when test="${dashboardAvailable}"><fmt:formatNumber value="${ingredientCount}" groupingUsed="true" /></c:when><c:otherwise>Unavailable</c:otherwise></c:choose>
                </p>
                <p class="metric-foot">Ingredient records in this schema</p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Goods receipts</p>
                <p class="metric-value">
                    <c:choose><c:when test="${dashboardAvailable}"><fmt:formatNumber value="${goodsReceiptCount}" groupingUsed="true" /></c:when><c:otherwise>Unavailable</c:otherwise></c:choose>
                </p>
                <p class="metric-foot">Recorded delivery receipts</p>
            </article>
        </section>

        <section class="content-grid" aria-label="Procurement and inventory summaries">
            <article class="panel panel-wide">
                <div class="panel-heading">
                    <div>
                        <p class="eyebrow">Verified delivery records</p>
                        <h2>Procurement quantities</h2>
                    </div>
                    <span class="unit-tag">Grouped by unit</span>
                </div>
                <p class="panel-note">Cancelled orders are excluded. Short quantity is ordered quantity minus verified receipts to date, calculated per order line. It is not, by itself, a finding of vendor fault.</p>
                <div class="table-wrap" role="region" tabindex="0" aria-label="Procurement quantities by ingredient unit">
                    <table>
                        <caption class="visually-hidden">Order and verified delivery quantities grouped by ingredient unit</caption>
                        <thead>
                            <tr>
                                <th scope="col">Unit</th>
                                <th scope="col">Ordered</th>
                                <th scope="col">Received</th>
                                <th scope="col">Accepted</th>
                                <th scope="col">Rejected</th>
                                <th scope="col">Short</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:choose>
                                <c:when test="${not dashboardAvailable}"><tr><td colspan="6" class="empty-state">Quantity data unavailable.</td></tr></c:when>
                                <c:when test="${empty quantityRows}"><tr><td colspan="6" class="empty-state">No non-cancelled purchase-order items recorded.</td></tr></c:when>
                                <c:otherwise>
                                    <c:forEach var="row" items="${quantityRows}">
                                        <tr>
                                            <th scope="row"><c:out value="${row.baseUnit}" /></th>
                                            <td><fmt:formatNumber value="${row.orderedQty}" maxFractionDigits="3" groupingUsed="true" /></td>
                                            <td><fmt:formatNumber value="${row.receivedQty}" maxFractionDigits="3" groupingUsed="true" /></td>
                                            <td><fmt:formatNumber value="${row.acceptedQty}" maxFractionDigits="3" groupingUsed="true" /></td>
                                            <td><fmt:formatNumber value="${row.rejectedQty}" maxFractionDigits="3" groupingUsed="true" /></td>
                                            <td><fmt:formatNumber value="${row.shortQty}" maxFractionDigits="3" groupingUsed="true" /></td>
                                        </tr>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </tbody>
                    </table>
                </div>
            </article>

            <article class="panel">
                <div class="panel-heading">
                    <div>
                        <p class="eyebrow">Configured thresholds</p>
                        <h2>Low stock</h2>
                    </div>
                    <p class="panel-count"><c:choose><c:when test="${dashboardAvailable}"><fmt:formatNumber value="${lowStockCount}" groupingUsed="true" /></c:when><c:otherwise>—</c:otherwise></c:choose></p>
                </div>
                <p class="panel-note">Ingredient balances below their block-specific reorder level.</p>
                <div class="table-wrap" role="region" tabindex="0" aria-label="Ingredients below reorder level">
                    <table>
                        <caption class="visually-hidden">Ingredients below configured reorder level</caption>
                        <thead>
                            <tr><th scope="col">Block and ingredient</th><th scope="col">On hand</th><th scope="col">Reorder at</th></tr>
                        </thead>
                        <tbody>
                            <c:choose>
                                <c:when test="${not dashboardAvailable}"><tr><td colspan="3" class="empty-state">Stock data unavailable.</td></tr></c:when>
                                <c:when test="${empty lowStockRows}"><tr><td colspan="3" class="empty-state">No ingredients are below threshold.</td></tr></c:when>
                                <c:otherwise>
                                    <c:forEach var="row" items="${lowStockRows}">
                                        <tr>
                                            <th scope="row"><c:out value="${row.ingredientName}" /><span class="sub-cell"><c:out value="${row.blockName}" /></span></th>
                                            <td><fmt:formatNumber value="${row.onHandQty}" maxFractionDigits="3" groupingUsed="true" /> <c:out value="${row.baseUnit}" /></td>
                                            <td><fmt:formatNumber value="${row.reorderLevel}" maxFractionDigits="3" groupingUsed="true" /> <c:out value="${row.baseUnit}" /></td>
                                        </tr>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </tbody>
                    </table>
                </div>
                <c:if test="${dashboardAvailable and lowStockCount gt 8}"><p class="panel-foot">Showing the first 8 stock alerts.</p></c:if>
            </article>
        </section>

        <section class="panel leftovers-panel" aria-labelledby="leftovers-title">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">Mess records</p>
                    <h2 id="leftovers-title">Recent measured leftovers</h2>
                </div>
                <span class="unit-tag">Latest 8 records</span>
            </div>
            <p class="panel-note">Leftover quantity is the recorded measurement, not proof that food was discarded. Per-capita leftovers are shown only when a measurement exists and meals served is greater than zero.</p>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Recent measured leftovers by day and block">
                <table>
                    <caption class="visually-hidden">Recent daily mess records and measured leftovers</caption>
                    <thead>
                        <tr>
                            <th scope="col">Date</th>
                            <th scope="col">Block</th>
                            <th scope="col">Meals served</th>
                            <th scope="col">Prepared</th>
                            <th scope="col">Measured leftover</th>
                            <th scope="col">Leftover per meal</th>
                            <th scope="col">Measurement method</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not dashboardAvailable}"><tr><td colspan="7" class="empty-state">Mess data unavailable.</td></tr></c:when>
                            <c:when test="${empty recentMessRows}"><tr><td colspan="7" class="empty-state">No daily mess records found.</td></tr></c:when>
                            <c:otherwise>
                                <c:forEach var="row" items="${recentMessRows}">
                                    <tr>
                                        <td><fmt:formatDate value="${row.recordDate}" pattern="dd MMM yyyy" /></td>
                                        <td><c:out value="${row.blockName}" /></td>
                                        <td><fmt:formatNumber value="${row.mealsServed}" groupingUsed="true" /></td>
                                        <td><c:choose><c:when test="${row.preparedKg ne null}"><fmt:formatNumber value="${row.preparedKg}" maxFractionDigits="3" groupingUsed="true" /> kg</c:when><c:otherwise>Not recorded</c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${row.leftoverKg ne null}"><fmt:formatNumber value="${row.leftoverKg}" maxFractionDigits="3" groupingUsed="true" /> kg</c:when><c:otherwise>Not measured</c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${row.perCapitaLeftoverKg ne null}"><fmt:formatNumber value="${row.perCapitaLeftoverKg}" maxFractionDigits="3" groupingUsed="true" /> kg</c:when><c:otherwise>—</c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${not empty row.measurementMethod}"><c:out value="${row.measurementMethod}" /></c:when><c:otherwise>Not recorded</c:otherwise></c:choose></td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <footer class="page-footer">
            <p>Procurement discrepancies and measured food leftovers are reported separately.</p>
            <p>MessAudit · Procurement &amp; Operations Management</p>
        </footer>
    </main>
</body>
</html>

