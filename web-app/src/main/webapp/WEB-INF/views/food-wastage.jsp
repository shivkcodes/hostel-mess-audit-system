<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Food Wastage &amp; Consumption | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>


    <main id="food-wastage-main" class="page-shell">
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Mess operations</p>
                <h1 id="page-title">Food Wastage &amp; Consumption</h1>
                <p class="intro">Review meals served, prepared quantities, and measured leftovers by day and mess block. The database does not record whether leftovers were discarded.</p>
            </div>
            <c:choose>
                <c:when test="${foodWastageAvailable}"><p class="data-note"><span class="live-dot" aria-hidden="true"></span> Live database figures</p></c:when>
                <c:otherwise><p class="data-note"><span class="offline-dot" aria-hidden="true"></span> Database unavailable</p></c:otherwise>
            </c:choose>
        </section>

        <c:if test="${not foodWastageAvailable}">
            <div class="notice notice-error" role="alert"><c:out value="${foodWastageError}" /></div>
        </c:if>
        <c:if test="${not empty filterErrors}">
            <div class="notice notice-error" role="alert" aria-labelledby="filter-errors-title">
                <h2 id="filter-errors-title">Some filters were ignored</h2>
                <ul>
                    <c:forEach var="error" items="${filterErrors}"><li><c:out value="${error}" /></li></c:forEach>
                </ul>
            </div>
        </c:if>

        <section class="panel wastage-filter-panel" aria-labelledby="wastage-filters-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Report scope</p><h2 id="wastage-filters-title">Filters</h2></div>
                <span class="unit-tag">Read only</span>
            </div>
            <form id="wastage-filter-form" action="${pageContext.request.contextPath}/food-wastage" method="get">
                <div class="wastage-filter-grid">
                    <div class="form-field">
                        <label for="from-date">From date</label>
                        <input id="from-date" name="fromDate" type="date" value="${fn:escapeXml(selectedFromDate)}" <c:if test="${not foodWastageAvailable}">disabled</c:if>>
                    </div>
                    <div class="form-field">
                        <label for="to-date">To date</label>
                        <input id="to-date" name="toDate" type="date" value="${fn:escapeXml(selectedToDate)}" <c:if test="${not foodWastageAvailable}">disabled</c:if>>
                    </div>
                    <div class="form-field">
                        <label for="block-id">Mess block</label>
                        <select id="block-id" name="blockId" <c:if test="${not foodWastageAvailable}">disabled</c:if>>
                            <option value="">All blocks</option>
                            <c:forEach var="block" items="${blocks}">
                                <c:choose>
                                    <c:when test="${selectedBlockId eq block.blockId}"><option value="${block.blockId}" selected="selected"><c:out value="${block.blockName}" /></option></c:when>
                                    <c:otherwise><option value="${block.blockId}"><c:out value="${block.blockName}" /></option></c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="wastage-filter-actions">
                        <button class="button button-primary" type="submit" <c:if test="${not foodWastageAvailable}">disabled</c:if>>Apply filters</button>
                        <a class="button button-secondary" href="${pageContext.request.contextPath}/food-wastage">Clear filters</a>
                    </div>
                </div>
            </form>
            <p id="wastage-loading" class="inventory-loading" role="status" aria-live="polite" hidden>Refreshing food records…</p>
        </section>

        <section class="metric-grid wastage-metric-grid" aria-label="Filtered mess record summary">
            <article class="metric-card">
                <p class="metric-label">Daily records</p>
                <p class="metric-value"><c:choose><c:when test="${foodWastageAvailable}"><fmt:formatNumber value="${summary.recordCount}" groupingUsed="true" /></c:when><c:otherwise>Unavailable</c:otherwise></c:choose></p>
                <p class="metric-foot">Records in the selected period</p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Meals served</p>
                <p class="metric-value"><c:choose><c:when test="${foodWastageAvailable}"><fmt:formatNumber value="${summary.mealsServed}" groupingUsed="true" /></c:when><c:otherwise>Unavailable</c:otherwise></c:choose></p>
                <p class="metric-foot">Includes records with missing leftover measurements</p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Measured leftovers</p>
                <p class="metric-value">
                    <c:choose>
                        <c:when test="${not foodWastageAvailable}">Unavailable</c:when>
                        <c:when test="${summary.measuredLeftoverKg ne null}"><fmt:formatNumber value="${summary.measuredLeftoverKg}" maxFractionDigits="3" groupingUsed="true" /> kg</c:when>
                        <c:otherwise>Not measured</c:otherwise>
                    </c:choose>
                </p>
                <p class="metric-foot"><c:if test="${foodWastageAvailable}"><fmt:formatNumber value="${summary.measuredRecordCount}" groupingUsed="true" /> of <fmt:formatNumber value="${summary.recordCount}" groupingUsed="true" /> records have a measurement</c:if></p>
            </article>
            <article class="metric-card">
                <p class="metric-label">Weighted leftover per meal</p>
                <p class="metric-value">
                    <c:choose>
                        <c:when test="${not foodWastageAvailable}">Unavailable</c:when>
                        <c:when test="${summary.weightedPerMealKg ne null}"><fmt:formatNumber value="${summary.weightedPerMealKg}" maxFractionDigits="3" groupingUsed="true" /> kg</c:when>
                        <c:otherwise>Not available</c:otherwise>
                    </c:choose>
                </p>
                <p class="metric-foot"><c:if test="${foodWastageAvailable}">Uses <fmt:formatNumber value="${summary.perMealRecordCount}" groupingUsed="true" /> measured records with meals served &gt; 0</c:if></p>
            </article>
        </section>

        <div class="notice notice-info wastage-method-note" role="note">
            <strong>How to read these figures:</strong> “Leftovers” are the measured amounts in <code>LEFTOVER_KG</code>, not confirmed discarded food. Per-meal figures use only records with a leftover measurement and at least one meal served. Missing measurements and zero-meal records are excluded from that ratio, not treated as zero.
        </div>

        <section class="panel analytics-panel" aria-labelledby="wastage-analytics-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Daily measurements</p><h2 id="wastage-analytics-title">Prepared food and measured leftovers</h2></div>
                <span class="unit-tag">Kilograms</span>
            </div>
            <p class="panel-note">Shows up to the 90 most recent dates with matching records, under the date and block filters above. Missing measurements remain gaps; measured leftovers are not confirmed discarded food.</p>
            <div class="analytics-chart" data-analytics-chart="wastage"
                 data-chart-data="<c:out value='${wastageChartJson}' />"
                 data-chart-available="${foodWastageAvailable}"
                 data-chart-count="${wastageChartDateCount}">
                <div class="analytics-chart-toolbar">
                    <div class="analytics-chart-context">Filters: <c:choose><c:when test="${not empty selectedBlockId}">selected mess block</c:when><c:otherwise>all mess blocks</c:otherwise></c:choose><span class="sub-cell">Use the date and block filters above to change this chart.</span></div>
                    <div class="analytics-legend" data-chart-legend aria-label="Chart series"></div>
                </div>
                <p class="analytics-chart-status" data-chart-status role="status" aria-live="polite">Enable JavaScript to view the interactive chart; daily measurements remain available in the tables below.</p>
                <div class="analytics-chart-viewport" data-chart-viewport tabindex="0" role="region" aria-label="Scrollable food measurement chart"></div>
            </div>
        </section>

        <section class="panel wastage-panel" aria-labelledby="block-summary-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Filtered period</p><h2 id="block-summary-title">Summary by mess block</h2></div>
                <c:if test="${foodWastageAvailable}"><span class="unit-tag"><fmt:formatNumber value="${fn:length(blockSummaries)}" groupingUsed="true" /> blocks</span></c:if>
            </div>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Consumption summary by mess block; scroll horizontally to see all columns">
                <table class="wastage-summary-table">
                    <caption class="visually-hidden">Daily record, meal, preparation, and measured leftover totals by block</caption>
                    <thead><tr><th scope="col">Mess block</th><th scope="col">Records</th><th scope="col">Meals served</th><th scope="col">Prepared kg</th><th scope="col">Prepared measurements</th><th scope="col">Measured leftovers kg</th><th scope="col">Leftover measurements</th><th scope="col">Weighted kg per meal</th></tr></thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not foodWastageAvailable}"><tr><td colspan="8" class="empty-state">Block summaries are unavailable.</td></tr></c:when>
                            <c:when test="${empty blockSummaries}"><tr><td colspan="8" class="empty-state">No mess records match these filters.</td></tr></c:when>
                            <c:otherwise>
                                <c:forEach var="row" items="${blockSummaries}">
                                    <tr>
                                        <th scope="row"><c:out value="${row.groupName}" /></th>
                                        <td><fmt:formatNumber value="${row.recordCount}" groupingUsed="true" /></td>
                                        <td><fmt:formatNumber value="${row.mealsServed}" groupingUsed="true" /></td>
                                        <td><c:choose><c:when test="${row.preparedKg ne null}"><fmt:formatNumber value="${row.preparedKg}" maxFractionDigits="3" groupingUsed="true" /></c:when><c:otherwise>Not measured</c:otherwise></c:choose></td>
                                        <td><fmt:formatNumber value="${row.preparedRecordCount}" groupingUsed="true" /></td>
                                        <td><c:choose><c:when test="${row.measuredLeftoverKg ne null}"><fmt:formatNumber value="${row.measuredLeftoverKg}" maxFractionDigits="3" groupingUsed="true" /></c:when><c:otherwise>Not measured</c:otherwise></c:choose></td>
                                        <td><fmt:formatNumber value="${row.measuredRecordCount}" groupingUsed="true" /></td>
                                        <td><c:choose><c:when test="${row.weightedPerMealKg ne null}"><fmt:formatNumber value="${row.weightedPerMealKg}" maxFractionDigits="3" groupingUsed="true" /></c:when><c:otherwise>Not available</c:otherwise></c:choose></td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="panel wastage-panel" aria-labelledby="date-summary-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Daily trend</p><h2 id="date-summary-title">Summary by date</h2></div>
                <c:if test="${foodWastageAvailable}"><span class="unit-tag"><fmt:formatNumber value="${fn:length(dateSummaries)}" groupingUsed="true" /> dates</span></c:if>
            </div>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Consumption summary by date; scroll horizontally to see all columns">
                <table class="wastage-date-table">
                    <caption class="visually-hidden">Daily meal, prepared quantity, and measured leftover totals</caption>
                    <thead><tr><th scope="col">Date</th><th scope="col">Blocks</th><th scope="col">Records</th><th scope="col">Meals served</th><th scope="col">Prepared kg</th><th scope="col">Measured leftovers kg</th><th scope="col">Leftover measurements</th><th scope="col">Weighted kg per meal</th></tr></thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not foodWastageAvailable}"><tr><td colspan="8" class="empty-state">Daily summaries are unavailable.</td></tr></c:when>
                            <c:when test="${empty dateSummaries}"><tr><td colspan="8" class="empty-state">No mess records match these filters.</td></tr></c:when>
                            <c:otherwise>
                                <c:forEach var="row" items="${dateSummaries}">
                                    <tr>
                                        <th scope="row"><fmt:formatDate value="${row.reportDate}" pattern="dd MMM yyyy" /></th>
                                        <td><fmt:formatNumber value="${row.blockCount}" groupingUsed="true" /></td>
                                        <td><fmt:formatNumber value="${row.recordCount}" groupingUsed="true" /></td>
                                        <td><fmt:formatNumber value="${row.mealsServed}" groupingUsed="true" /></td>
                                        <td><c:choose><c:when test="${row.preparedKg ne null}"><fmt:formatNumber value="${row.preparedKg}" maxFractionDigits="3" groupingUsed="true" /></c:when><c:otherwise>Not measured</c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${row.measuredLeftoverKg ne null}"><fmt:formatNumber value="${row.measuredLeftoverKg}" maxFractionDigits="3" groupingUsed="true" /></c:when><c:otherwise>Not measured</c:otherwise></c:choose></td>
                                        <td><fmt:formatNumber value="${row.measuredRecordCount}" groupingUsed="true" /></td>
                                        <td><c:choose><c:when test="${row.weightedPerMealKg ne null}"><fmt:formatNumber value="${row.weightedPerMealKg}" maxFractionDigits="3" groupingUsed="true" /></c:when><c:otherwise>Not available</c:otherwise></c:choose></td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="panel wastage-panel" aria-labelledby="daily-records-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Recorded measurements</p><h2 id="daily-records-title">Daily mess records</h2></div>
                <c:if test="${foodWastageAvailable}"><span class="unit-tag">Latest <fmt:formatNumber value="${fn:length(dailyRecords)}" groupingUsed="true" /> matching records</span></c:if>
            </div>
            <p class="panel-note">The daily list shows up to the latest 100 matching records. Aggregates above use all records that match the selected filters.</p>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Daily mess records; scroll horizontally to see all columns">
                <table class="wastage-record-table">
                    <caption class="visually-hidden">Daily meals served, prepared food, measured leftovers, and measurement notes</caption>
                    <thead><tr><th scope="col">Date</th><th scope="col">Mess block</th><th scope="col">Meals served</th><th scope="col">Prepared kg</th><th scope="col">Measured leftovers kg</th><th scope="col">Leftover per meal</th><th scope="col">Measurement method</th><th scope="col">Notes</th></tr></thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not foodWastageAvailable}"><tr><td colspan="8" class="empty-state">Daily records are unavailable.</td></tr></c:when>
                            <c:when test="${empty dailyRecords}"><tr><td colspan="8" class="empty-state">No daily mess records match these filters.</td></tr></c:when>
                            <c:otherwise>
                                <c:forEach var="row" items="${dailyRecords}">
                                    <tr>
                                        <th scope="row"><fmt:formatDate value="${row.recordDate}" pattern="dd MMM yyyy" /></th>
                                        <td><c:out value="${row.blockName}" /></td>
                                        <td><fmt:formatNumber value="${row.mealsServed}" groupingUsed="true" /></td>
                                        <td><c:choose><c:when test="${row.preparedKg ne null}"><fmt:formatNumber value="${row.preparedKg}" maxFractionDigits="3" groupingUsed="true" /> kg</c:when><c:otherwise>Not recorded</c:otherwise></c:choose></td>
                                        <td><c:choose><c:when test="${row.leftoverKg ne null}"><fmt:formatNumber value="${row.leftoverKg}" maxFractionDigits="3" groupingUsed="true" /> kg</c:when><c:otherwise>Not measured</c:otherwise></c:choose></td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${row.leftoverKg eq null}">Not measured</c:when>
                                                <c:when test="${row.mealsServed eq 0}">Not available (zero meals)</c:when>
                                                <c:otherwise><fmt:formatNumber value="${row.perMealLeftoverKg}" maxFractionDigits="3" groupingUsed="true" /> kg</c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td class="wastage-method-cell"><c:choose><c:when test="${not empty row.measurementMethod}"><c:out value="${row.measurementMethod}" /></c:when><c:otherwise>Not recorded</c:otherwise></c:choose></td>
                                        <td class="wastage-notes-cell"><c:choose><c:when test="${not empty row.notes}"><c:out value="${row.notes}" /></c:when><c:otherwise>—</c:otherwise></c:choose></td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <footer class="page-footer">
            <p>Measured leftovers are not a finding of disposal or vendor fault.</p>
            <p>Read-only academic reporting from the configured Oracle database.</p>
        </footer>
    </main>
    <script>
        document.getElementById("wastage-filter-form").addEventListener("submit", function () {
            document.getElementById("food-wastage-main").setAttribute("aria-busy", "true");
            document.getElementById("wastage-loading").hidden = false;
        });
    </script>
    <script src="${pageContext.request.contextPath}/js/analytics-charts.js?v=20261028-12" defer></script>
</body>
</html>
