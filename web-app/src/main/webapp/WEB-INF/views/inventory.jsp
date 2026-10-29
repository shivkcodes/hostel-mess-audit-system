<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Inventory | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>


    <main id="inventory-main" class="page-shell">
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Read-only stock view</p>
                <h1 id="page-title">Inventory</h1>
                <p class="intro">Review recorded stock balances by mess block and ingredient, then trace recent movements in the inventory ledger.</p>
            </div>
        </section>

        <c:if test="${not inventoryAvailable}">
            <div class="notice notice-error" role="alert"><c:out value="${inventoryError}" /></div>
        </c:if>
        <c:if test="${not empty filterErrors}">
            <div class="notice notice-error" role="alert" aria-labelledby="filter-errors-title">
                <h2 id="filter-errors-title">Some filters were ignored</h2>
                <ul>
                    <c:forEach var="error" items="${filterErrors}">
                        <li><c:out value="${error}" /></li>
                    </c:forEach>
                </ul>
            </div>
        </c:if>

        <section class="panel inventory-filter-panel" aria-labelledby="inventory-filters-title">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">Narrow the view</p>
                    <h2 id="inventory-filters-title">Inventory filters</h2>
                </div>
                <span class="unit-tag">Read only</span>
            </div>
            <form id="inventory-filter-form" action="${pageContext.request.contextPath}/inventory" method="get">
                <div class="inventory-filter-grid">
                    <div class="form-field">
                        <label for="filter-block">Mess block</label>
                        <select id="filter-block" name="blockId">
                            <option value="">All blocks</option>
                            <c:forEach var="block" items="${blocks}">
                                <c:choose>
                                    <c:when test="${selectedBlockId eq block.blockId}">
                                        <option value="${block.blockId}" selected="selected"><c:out value="${block.blockName}" /></option>
                                    </c:when>
                                    <c:otherwise>
                                        <option value="${block.blockId}"><c:out value="${block.blockName}" /></option>
                                    </c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="form-field">
                        <label for="filter-ingredient">Ingredient</label>
                        <select id="filter-ingredient" name="ingredientId">
                            <option value="">All ingredients</option>
                            <c:forEach var="ingredient" items="${ingredients}">
                                <c:choose>
                                    <c:when test="${selectedIngredientId eq ingredient.ingredientId}">
                                        <option value="${ingredient.ingredientId}" selected="selected"><c:out value="${ingredient.ingredientName}" /> (<c:out value="${ingredient.baseUnit}" />)</option>
                                    </c:when>
                                    <c:otherwise>
                                        <option value="${ingredient.ingredientId}"><c:out value="${ingredient.ingredientName}" /> (<c:out value="${ingredient.baseUnit}" />)</option>
                                    </c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="form-field">
                        <label for="filter-transaction-type">Transaction type</label>
                        <select id="filter-transaction-type" name="transactionType">
                            <option value="">All types</option>
                            <c:forEach var="type" items="${transactionTypes}">
                                <c:choose>
                                    <c:when test="${selectedTransactionType eq type}">
                                        <option value="${type}" selected="selected"><c:out value="${type}" /></option>
                                    </c:when>
                                    <c:otherwise>
                                        <option value="${type}"><c:out value="${type}" /></option>
                                    </c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="inventory-filter-actions">
                        <button class="button button-primary" type="submit" <c:if test="${not inventoryAvailable}">disabled</c:if>>Apply filters</button>
                        <a class="button button-secondary" href="${pageContext.request.contextPath}/inventory">Clear filters</a>
                    </div>
                </div>
            </form>
            <p id="inventory-loading" class="inventory-loading" role="status" aria-live="polite" hidden>Refreshing inventory data…</p>
        </section>

        <section class="panel analytics-panel" aria-labelledby="inventory-analytics-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Current balances</p><h2 id="inventory-analytics-title">On-hand stock vs reorder level</h2></div>
                <span class="unit-tag">Current snapshot</span>
            </div>
            <p class="panel-note">Uses the balances matching the filters above. It charts up to 12 rows per unit (currently <fmt:formatNumber value="${inventoryChartIncludedCount}" groupingUsed="true" /> of <fmt:formatNumber value="${inventoryChartTotalCount}" groupingUsed="true" /> matching balances), prioritizing low stock and then sorting by block and ingredient name. Units are shown separately.</p>
            <div class="analytics-chart" data-analytics-chart="inventory"
                 data-chart-data="<c:out value='${inventoryChartJson}' />"
                 data-chart-available="${inventoryAnalyticsAvailable}">
                <div class="analytics-chart-toolbar">
                    <div class="form-field analytics-unit-field">
                        <label for="inventory-chart-unit">Ingredient unit</label>
                        <select id="inventory-chart-unit" data-chart-unit disabled><option>Chart data unavailable</option></select>
                    </div>
                    <div class="analytics-legend" data-chart-legend aria-label="Chart series"></div>
                </div>
                <p class="analytics-chart-status" data-chart-status role="status" aria-live="polite">Enable JavaScript to view the interactive chart; the balance table remains available below.</p>
                <div class="analytics-chart-viewport" data-chart-viewport tabindex="0" role="region" aria-label="Scrollable inventory balance chart"></div>
            </div>
        </section>

        <section class="panel inventory-panel" aria-labelledby="stock-title">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">Current recorded quantities</p>
                    <h2 id="stock-title">Stock balances</h2>
                </div>
                <c:if test="${inventoryAvailable}">
                    <span class="unit-tag"><fmt:formatNumber value="${fn:length(stockRows)}" groupingUsed="true" /> balance rows</span>
                </c:if>
            </div>
            <p class="panel-note">Low stock means on-hand quantity is below the block-specific reorder level. Only balance rows recorded in the database are shown.</p>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Inventory balances; scroll horizontally to see all columns">
                <table class="inventory-stock-table">
                    <caption class="visually-hidden">Current stock, reorder level, and low-stock status by mess block and ingredient</caption>
                    <thead>
                        <tr>
                            <th scope="col">Mess block</th>
                            <th scope="col">Ingredient</th>
                            <th scope="col">On hand</th>
                            <th scope="col">Reorder level</th>
                            <th scope="col">Status</th>
                            <th scope="col">Last updated</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not inventoryAvailable}">
                                <tr><td colspan="6" class="empty-state">Stock balances are unavailable right now.</td></tr>
                            </c:when>
                            <c:when test="${empty stockRows}">
                                <tr><td colspan="6" class="empty-state">No inventory balances match these filters. Accepted goods from a processed receipt will appear here.</td></tr>
                            </c:when>
                            <c:otherwise>
                                <c:forEach var="row" items="${stockRows}">
                                    <tr>
                                        <th scope="row"><c:out value="${row.blockName}" /></th>
                                        <td><c:out value="${row.ingredientName}" /><span class="sub-cell"><c:out value="${row.baseUnit}" /></span></td>
                                        <td><fmt:formatNumber value="${row.onHandQty}" maxFractionDigits="3" groupingUsed="true" /> <c:out value="${row.baseUnit}" /></td>
                                        <td><fmt:formatNumber value="${row.reorderLevel}" maxFractionDigits="3" groupingUsed="true" /> <c:out value="${row.baseUnit}" /></td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${row.lowStock}"><span class="stock-status stock-low">Low stock</span></c:when>
                                                <c:otherwise><span class="stock-status stock-ok">At or above level</span></c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td><fmt:formatDate value="${row.updatedAt}" pattern="dd MMM yyyy, HH:mm" /></td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="panel inventory-panel" aria-labelledby="transactions-title">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">Stock movement ledger</p>
                    <h2 id="transactions-title">Recent inventory transactions</h2>
                </div>
                <c:if test="${inventoryAvailable}"><span class="unit-tag">Latest 100</span></c:if>
            </div>
            <p class="panel-note">Transaction history is shown newest first and uses the same block and ingredient filters above, plus the transaction type filter.</p>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Recent inventory transactions; scroll horizontally to see all columns">
                <table class="inventory-transactions-table">
                    <caption class="visually-hidden">Recent inventory movements, references, and resulting balances</caption>
                    <thead>
                        <tr>
                            <th scope="col">Timestamp</th>
                            <th scope="col">Reference</th>
                            <th scope="col">Type</th>
                            <th scope="col">Mess block</th>
                            <th scope="col">Ingredient</th>
                            <th scope="col">Quantity change</th>
                            <th scope="col">Balance after</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not inventoryAvailable}">
                                <tr><td colspan="7" class="empty-state">Transaction history is unavailable right now.</td></tr>
                            </c:when>
                            <c:when test="${empty transactionRows}">
                                <tr><td colspan="7" class="empty-state">No inventory transactions match these filters.</td></tr>
                            </c:when>
                            <c:otherwise>
                                <c:forEach var="row" items="${transactionRows}">
                                    <tr>
                                        <td><fmt:formatDate value="${row.createdAt}" pattern="dd MMM yyyy, HH:mm:ss" /></td>
                                        <th scope="row"><c:out value="${row.transactionReference}" /></th>
                                        <td><span class="transaction-type"><c:out value="${row.transactionType}" /></span></td>
                                        <td><c:out value="${row.blockName}" /></td>
                                        <td><c:out value="${row.ingredientName}" /><span class="sub-cell"><c:out value="${row.baseUnit}" /></span></td>
                                        <td class="quantity-change">
                                            <c:if test="${row.quantityDelta gt 0}">+</c:if><fmt:formatNumber value="${row.quantityDelta}" maxFractionDigits="3" groupingUsed="true" /> <c:out value="${row.baseUnit}" />
                                        </td>
                                        <td><fmt:formatNumber value="${row.balanceAfterQty}" maxFractionDigits="3" groupingUsed="true" /> <c:out value="${row.baseUnit}" /></td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
            <c:if test="${inventoryAvailable and fn:length(transactionRows) eq 100}">
                <p class="panel-foot">Showing the latest 100 matching transaction rows.</p>
            </c:if>
        </section>

        <footer class="page-footer">
            <p>This page is read-only. Stock changes are recorded through the goods-receipt workflow.</p>
            <p>MessAudit · Procurement &amp; Operations Management</p>
        </footer>
    </main>
    <script>
        document.getElementById("inventory-filter-form").addEventListener("submit", function () {
            document.getElementById("inventory-main").setAttribute("aria-busy", "true");
            document.getElementById("inventory-loading").hidden = false;
        });
    </script>
    <script src="${pageContext.request.contextPath}/js/analytics-charts.js?v=20261028-12" defer></script>
</body>
</html>
