<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Procurement | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>


    <main class="page-shell">
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Purchasing workflow</p>
                <h1 id="page-title">Procurement</h1>
                <p class="intro">Create purchase orders for active vendors and mess blocks, then review their recorded status and approved values.</p>
            </div>
        </section>

        <c:if test="${created}">
            <div class="notice notice-success" role="status">Purchase order and its item lines were saved.</div>
        </c:if>
        <c:if test="${not empty procurementError}">
            <div class="notice notice-error" role="alert"><c:out value="${procurementError}" /></div>
        </c:if>
        <c:if test="${not empty filterErrors}">
            <div class="notice notice-error" role="alert" aria-labelledby="procurement-filter-errors-title">
                <h2 id="procurement-filter-errors-title">Some chart filters need attention</h2>
                <ul><c:forEach var="error" items="${filterErrors}"><li><c:out value="${error}" /></li></c:forEach></ul>
            </div>
        </c:if>
        <c:if test="${not empty formErrors}">
            <div class="notice notice-error" role="alert" aria-labelledby="form-errors-title">
                <h2 id="form-errors-title">Please review the form</h2>
                <ul>
                    <c:forEach var="error" items="${formErrors}">
                        <li><c:out value="${error}" /></li>
                    </c:forEach>
                </ul>
            </div>
        </c:if>

        <c:if test="${canManage}">
        <section class="panel form-panel" aria-labelledby="create-order-title">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">New record</p>
                    <h2 id="create-order-title">Create purchase order</h2>
                </div>
                <span class="unit-tag">At least one item</span>
            </div>
            <p class="panel-note">The approved amount must cover the listed item values. The order starts with the database default status, OPEN.</p>

            <form action="${pageContext.request.contextPath}/procurement" method="post" accept-charset="UTF-8">
                <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />">
                <div class="form-grid">
                    <div class="form-field">
                        <label for="po-number">Purchase-order number</label>
                        <input id="po-number" name="poNumber" type="text" maxlength="30" required autocomplete="off"
                               value="${fn:escapeXml(formData.poNumber)}" aria-describedby="po-number-help">
                        <small id="po-number-help" class="field-help">Must be unique; up to 30 characters.</small>
                    </div>
                    <div class="form-field">
                        <label for="vendor-id">Vendor</label>
                        <select id="vendor-id" name="vendorId" required>
                            <option value="">Choose an active vendor</option>
                            <c:forEach var="vendor" items="${vendors}">
                                <c:choose>
                                    <c:when test="${formData.vendorId eq vendor.vendorId}">
                                        <option value="${vendor.vendorId}" selected="selected"><c:out value="${vendor.vendorName}" /> (<c:out value="${vendor.vendorCode}" />)</option>
                                    </c:when>
                                    <c:otherwise>
                                        <option value="${vendor.vendorId}"><c:out value="${vendor.vendorName}" /> (<c:out value="${vendor.vendorCode}" />)</option>
                                    </c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="form-field">
                        <label for="block-id">Mess block</label>
                        <select id="block-id" name="blockId" required>
                            <option value="">Choose an active mess block</option>
                            <c:forEach var="block" items="${blocks}">
                                <c:choose>
                                    <c:when test="${formData.blockId eq block.blockId}">
                                        <option value="${block.blockId}" selected="selected"><c:out value="${block.blockName}" /> (<c:out value="${block.blockCode}" />)</option>
                                    </c:when>
                                    <c:otherwise>
                                        <option value="${block.blockId}"><c:out value="${block.blockName}" /> (<c:out value="${block.blockCode}" />)</option>
                                    </c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="form-field">
                        <label for="ordered-at">Order date</label>
                        <input id="ordered-at" name="orderedAt" type="date" required value="${fn:escapeXml(formData.orderedAt)}">
                    </div>
                    <div class="form-field">
                        <label for="expected-by">Expected delivery date <span class="optional-label">Optional</span></label>
                        <input id="expected-by" name="expectedBy" type="date" value="${fn:escapeXml(formData.expectedBy)}">
                    </div>
                    <div class="form-field">
                        <label for="approved-amount">Approved amount</label>
                        <input id="approved-amount" name="approvedAmount" type="number" min="0" step="0.01" required
                               value="${fn:escapeXml(formData.approvedAmount)}" aria-describedby="approved-amount-help">
                        <small id="approved-amount-help" class="field-help">Non-negative, up to two decimal places.</small>
                    </div>
                </div>

                <fieldset class="items-fieldset">
                    <legend>Purchase-order items</legend>
                    <p class="field-help">Enter a positive quantity and a non-negative unit price for every ingredient.</p>
                    <div id="item-rows" class="item-row-list" aria-live="polite">
                        <c:forEach var="item" items="${itemRows}" varStatus="rowStatus">
                            <div class="item-row" role="group" aria-label="Purchase-order item ${rowStatus.count}">
                                <div class="form-field">
                                    <label for="ingredient-${rowStatus.index}">Ingredient</label>
                                    <select id="ingredient-${rowStatus.index}" name="ingredientId" required>
                                        <option value="">Choose ingredient</option>
                                        <c:forEach var="ingredient" items="${ingredients}">
                                            <c:choose>
                                                <c:when test="${item.ingredientId eq ingredient.ingredientId}">
                                                    <option value="${ingredient.ingredientId}" selected="selected"><c:out value="${ingredient.ingredientName}" /> · <c:out value="${ingredient.baseUnit}" /></option>
                                                </c:when>
                                                <c:otherwise>
                                                    <option value="${ingredient.ingredientId}"><c:out value="${ingredient.ingredientName}" /> · <c:out value="${ingredient.baseUnit}" /></option>
                                                </c:otherwise>
                                            </c:choose>
                                        </c:forEach>
                                    </select>
                                </div>
                                <div class="form-field">
                                    <label for="ordered-qty-${rowStatus.index}">Quantity</label>
                                    <input id="ordered-qty-${rowStatus.index}" name="orderedQty" type="number" min="0.001" step="0.001" required
                                           value="${fn:escapeXml(item.orderedQty)}">
                                </div>
                                <div class="form-field">
                                    <label for="unit-price-${rowStatus.index}">Unit price</label>
                                    <input id="unit-price-${rowStatus.index}" name="unitPrice" type="number" min="0" step="0.01" required
                                           value="${fn:escapeXml(item.unitPrice)}">
                                </div>
                                <button class="button button-secondary remove-item" type="button" aria-label="Remove purchase-order item ${rowStatus.count}">Remove</button>
                            </div>
                        </c:forEach>
                    </div>
                    <button class="button button-secondary add-item" id="add-item" type="button" aria-controls="item-rows">Add another item</button>
                </fieldset>

                <c:choose>
                    <c:when test="${not procurementAvailable or empty vendors or empty blocks or empty ingredients}">
                        <p class="field-help">An active vendor, block, and ingredient are needed before an order can be submitted.</p>
                        <button class="button button-primary" type="submit" disabled>Save purchase order</button>
                    </c:when>
                    <c:otherwise>
                        <button class="button button-primary" type="submit">Save purchase order</button>
                    </c:otherwise>
                </c:choose>
            </form>
        </section>
        </c:if>
        <c:if test="${not canManage}">
            <div class="notice notice-info" role="note">Purchase-order creation is restricted to administrator accounts.</div>
        </c:if>

        <section class="panel orders-panel" aria-labelledby="orders-title">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">Existing records</p>
                    <h2 id="orders-title">Purchase orders</h2>
                </div>
                <span class="unit-tag"><fmt:formatNumber value="${purchaseOrderCount}" groupingUsed="true" /> records</span>
            </div>
            <p class="panel-note">Item value is the sum of each recorded ordered quantity multiplied by its unit price. Delivery receipts are not recorded from this page.</p>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Purchase orders; scroll horizontally to see all columns">
                <table class="orders-table">
                    <caption class="visually-hidden">Purchase-order status, dates, approved amounts, and item totals</caption>
                    <thead>
                        <tr>
                            <th scope="col">Order number</th>
                            <th scope="col">Vendor</th>
                            <th scope="col">Mess block</th>
                            <th scope="col">Status</th>
                            <th scope="col">Ordered</th>
                            <th scope="col">Expected</th>
                            <th scope="col">Items</th>
                            <th scope="col">Item value</th>
                            <th scope="col">Approved</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not procurementAvailable}">
                                <tr><td colspan="9" class="empty-state">Purchase-order data is unavailable.</td></tr>
                            </c:when>
                            <c:when test="${empty purchaseOrders}">
                                <tr><td colspan="9" class="empty-state">No purchase orders have been recorded yet.</td></tr>
                            </c:when>
                            <c:otherwise>
                                <c:forEach var="order" items="${purchaseOrders}">
                                    <tr>
                                        <th scope="row"><c:out value="${order.poNumber}" /></th>
                                        <td><c:out value="${order.vendorName}" /></td>
                                        <td><c:out value="${order.blockName}" /></td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${order.status eq 'OPEN'}"><span class="status-pill status-open">Open</span></c:when>
                                                <c:when test="${order.status eq 'PARTIAL'}"><span class="status-pill status-partial">Partial</span></c:when>
                                                <c:when test="${order.status eq 'CLOSED'}"><span class="status-pill status-closed">Closed</span></c:when>
                                                <c:when test="${order.status eq 'CANCELLED'}"><span class="status-pill status-cancelled">Cancelled</span></c:when>
                                                <c:otherwise><span class="status-pill"><c:out value="${order.status}" /></span></c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td><fmt:formatDate value="${order.orderedAt}" pattern="dd MMM yyyy" /></td>
                                        <td><c:choose><c:when test="${order.expectedBy ne null}"><fmt:formatDate value="${order.expectedBy}" pattern="dd MMM yyyy" /></c:when><c:otherwise>Not set</c:otherwise></c:choose></td>
                                        <td><fmt:formatNumber value="${order.itemCount}" groupingUsed="true" /></td>
                                        <td><fmt:formatNumber value="${order.orderValue}" minFractionDigits="2" maxFractionDigits="2" groupingUsed="true" /></td>
                                        <td><fmt:formatNumber value="${order.approvedAmount}" minFractionDigits="2" maxFractionDigits="2" groupingUsed="true" /></td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="panel analytics-panel" aria-labelledby="procurement-analytics-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Order history</p><h2 id="procurement-analytics-title">Purchase activity by month</h2></div>
                <span class="unit-tag">Ordered quantity</span>
            </div>
            <p class="panel-note">Monthly purchase-order item quantities are grouped by ingredient unit. Cancelled orders are excluded; this chart does not show order counts or financial spend.</p>
            <form class="analytics-filter-form" action="${pageContext.request.contextPath}/procurement" method="get">
                <div class="form-field"><label for="activity-from">Order date from</label><input id="activity-from" name="fromDate" type="date" value="<c:out value='${selectedActivityFromDate}' />"></div>
                <div class="form-field"><label for="activity-to">Order date through</label><input id="activity-to" name="toDate" type="date" value="<c:out value='${selectedActivityToDate}' />"></div>
                <div class="analytics-filter-actions">
                    <button class="button button-primary" type="submit" <c:if test="${not procurementAvailable}">disabled</c:if>>Apply chart dates</button>
                    <a class="button button-secondary" href="${pageContext.request.contextPath}/procurement">Clear dates</a>
                </div>
            </form>
            <p class="panel-note">The date range applies to this chart; the purchase-order table above continues to show all recorded orders.</p>
            <div class="analytics-chart" data-analytics-chart="procurement"
                 data-chart-data="<c:out value='${procurementChartJson}' />"
                 data-chart-available="${procurementAnalyticsAvailable}">
                <div class="analytics-chart-toolbar">
                    <div class="form-field analytics-unit-field">
                        <label for="procurement-chart-unit">Ingredient unit</label>
                        <select id="procurement-chart-unit" data-chart-unit disabled><option>Chart data unavailable</option></select>
                    </div>
                    <div class="analytics-legend" data-chart-legend aria-label="Chart series"></div>
                </div>
                <p class="analytics-chart-status" data-chart-status role="status" aria-live="polite">Enable JavaScript to view the interactive chart; purchase-order records remain available above.</p>
                <div class="analytics-chart-viewport" data-chart-viewport tabindex="0" role="region" aria-label="Scrollable monthly procurement chart"></div>
            </div>
        </section>

        <footer class="page-footer">
            <p>Purchase orders and delivery receipts are separate records.</p>
            <p>MessAudit · Procurement &amp; Operations Management</p>
        </footer>
    </main>

    <template id="item-row-template">
        <div class="item-row" role="group">
            <div class="form-field">
                <label>Ingredient
                    <select name="ingredientId" required>
                        <option value="">Choose ingredient</option>
                        <c:forEach var="ingredient" items="${ingredients}">
                            <option value="${ingredient.ingredientId}"><c:out value="${ingredient.ingredientName}" /> · <c:out value="${ingredient.baseUnit}" /></option>
                        </c:forEach>
                    </select>
                </label>
            </div>
            <div class="form-field">
                <label>Quantity
                    <input name="orderedQty" type="number" min="0.001" step="0.001" required>
                </label>
            </div>
            <div class="form-field">
                <label>Unit price
                    <input name="unitPrice" type="number" min="0" step="0.01" required>
                </label>
            </div>
            <button class="button button-secondary remove-item" type="button">Remove</button>
        </div>
    </template>

    <script>
        (() => {
            const rows = document.getElementById("item-rows");
            const template = document.getElementById("item-row-template");
            const addButton = document.getElementById("add-item");

            function updateRowLabels() {
                rows.querySelectorAll(".item-row").forEach((row, index) => {
                    const itemNumber = index + 1;
                    row.setAttribute("aria-label", `Purchase-order item ${itemNumber}`);
                    row.querySelector(".remove-item").setAttribute("aria-label", `Remove purchase-order item ${itemNumber}`);
                });
            }

            addButton.addEventListener("click", () => {
                const newRow = template.content.firstElementChild.cloneNode(true);
                rows.appendChild(newRow);
                updateRowLabels();
                newRow.querySelector("select").focus();
            });

            rows.addEventListener("click", (event) => {
                const removeButton = event.target.closest(".remove-item");
                if (!removeButton) {
                    return;
                }
                const row = removeButton.closest(".item-row");
                const nextFocusTarget = row.nextElementSibling?.querySelector("select")
                    || row.previousElementSibling?.querySelector("select");
                row.remove();
                updateRowLabels();
                if (nextFocusTarget) {
                    nextFocusTarget.focus();
                } else {
                    addButton.focus();
                }
            });

            updateRowLabels();
        })();
    </script>
    <script src="${pageContext.request.contextPath}/js/analytics-charts.js?v=20261028-12" defer></script>
</body>
</html>

