<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Goods Receipt | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>


    <main class="page-shell">
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Receiving workflow</p>
                <h1 id="page-title">Goods Receipt</h1>
                <p class="intro">Record deliveries against open purchase orders. Accepted quantities update the selected mess block’s stock; rejected goods and short deliveries remain separate quantities.</p>
            </div>
        </section>

        <c:if test="${created}">
            <div class="notice notice-success" role="status">The receipt was saved. Accepted quantities were posted to inventory in the same transaction.</div>
        </c:if>
        <c:if test="${not empty receiptError}">
            <div class="notice notice-error" role="alert"><c:out value="${receiptError}" /></div>
        </c:if>
        <c:if test="${not empty formErrors}">
            <div class="notice notice-error" role="alert" aria-labelledby="form-errors-title">
                <h2 id="form-errors-title">Please review the receipt</h2>
                <ul>
                    <c:forEach var="error" items="${formErrors}">
                        <li><c:out value="${error}" /></li>
                    </c:forEach>
                </ul>
            </div>
        </c:if>

        <section class="panel orders-panel" aria-labelledby="eligible-orders-title">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">Receipt eligible</p>
                    <h2 id="eligible-orders-title">Open purchase orders</h2>
                </div>
                <c:if test="${receiptAvailable}">
                    <span class="unit-tag"><fmt:formatNumber value="${fn:length(eligibleOrders)}" groupingUsed="true" /> eligible</span>
                </c:if>
            </div>
            <p class="panel-note">Only OPEN and PARTIAL orders with quantities still due are listed. Prior received quantities count against the order, including rejected goods.</p>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Open purchase orders eligible for receipts; scroll horizontally to see all columns">
                <table class="receipt-orders-table">
                    <caption class="visually-hidden">Open purchase orders and outstanding item counts</caption>
                    <thead>
                        <tr>
                            <th scope="col">Order number</th>
                            <th scope="col">Vendor</th>
                            <th scope="col">Mess block</th>
                            <th scope="col">Status</th>
                            <th scope="col">Ordered</th>
                            <th scope="col">Expected</th>
                            <th scope="col">Items due</th>
                            <th scope="col">Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not receiptAvailable}">
                                <tr><td colspan="8" class="empty-state">Purchase-order data is unavailable.</td></tr>
                            </c:when>
                            <c:when test="${empty eligibleOrders}">
                                <tr><td colspan="8" class="empty-state">There are no open purchase orders with quantities due.</td></tr>
                            </c:when>
                            <c:otherwise>
                                <c:forEach var="order" items="${eligibleOrders}">
                                    <tr>
                                        <th scope="row"><c:out value="${order.poNumber}" /></th>
                                        <td><c:out value="${order.vendorName}" /></td>
                                        <td><c:out value="${order.blockName}" /></td>
                                        <td><span class="status-pill ${order.status eq 'PARTIAL' ? 'status-partial' : 'status-open'}"><c:out value="${order.status}" /></span></td>
                                        <td><fmt:formatDate value="${order.orderedAt}" pattern="dd MMM yyyy" /></td>
                                        <td><c:choose><c:when test="${order.expectedBy ne null}"><fmt:formatDate value="${order.expectedBy}" pattern="dd MMM yyyy" /></c:when><c:otherwise>Not set</c:otherwise></c:choose></td>
                                        <td><fmt:formatNumber value="${order.outstandingItemCount}" groupingUsed="true" /></td>
                                        <td><a class="button button-secondary table-action" href="${pageContext.request.contextPath}/goods-receipts?poId=${order.poId}">Record receipt</a></td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="panel form-panel" aria-labelledby="select-order-title">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">New record</p>
                    <h2 id="select-order-title">Select a purchase order</h2>
                </div>
            </div>
            <form action="${pageContext.request.contextPath}/goods-receipts" method="get">
                <div class="form-grid receipt-select-grid">
                    <div class="form-field">
                        <label for="receipt-po-id">Purchase order</label>
                        <select id="receipt-po-id" name="poId" required <c:if test="${not receiptAvailable or empty eligibleOrders}">disabled</c:if>>
                            <option value="">Choose an open purchase order</option>
                            <c:forEach var="order" items="${eligibleOrders}">
                                <c:choose>
                                    <c:when test="${selectedOrder.poId eq order.poId}">
                                        <option value="${order.poId}" selected="selected"><c:out value="${order.poNumber}" /> · <c:out value="${order.vendorName}" /> · <c:out value="${order.blockName}" /></option>
                                    </c:when>
                                    <c:otherwise>
                                        <option value="${order.poId}"><c:out value="${order.poNumber}" /> · <c:out value="${order.vendorName}" /> · <c:out value="${order.blockName}" /></option>
                                    </c:otherwise>
                                </c:choose>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="receipt-select-action">
                        <button class="button button-secondary" type="submit" <c:if test="${not receiptAvailable or empty eligibleOrders}">disabled</c:if>>Load order items</button>
                    </div>
                </div>
            </form>
        </section>

        <c:if test="${not empty selectedOrder and receiptAvailable}">
            <c:choose>
            <c:when test="${canManage}">
            <section class="panel form-panel" aria-labelledby="record-receipt-title">
                <div class="panel-heading">
                    <div>
                        <p class="eyebrow">Delivery verification</p>
                        <h2 id="record-receipt-title">Record receipt for <c:out value="${selectedOrder.poNumber}" /></h2>
                    </div>
                    <span class="unit-tag"><c:out value="${selectedOrder.status}" /></span>
                </div>
                <p class="panel-note">Saving this form records the receipt as verified by the named person and posts accepted quantities to stock. Both operations commit together. Received quantity must equal accepted plus rejected quantity under the existing database constraint.</p>

                <form action="${pageContext.request.contextPath}/goods-receipts" method="post" accept-charset="UTF-8">
                    <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />">
                    <input type="hidden" name="poId" value="${selectedOrder.poId}">
                    <div class="form-grid">
                        <div class="form-field">
                            <label for="invoice-reference">Invoice reference</label>
                            <input id="invoice-reference" name="invoiceReference" type="text" maxlength="60" required autocomplete="off" value="${fn:escapeXml(receiptData.invoiceReference)}" aria-describedby="invoice-reference-help">
                            <small id="invoice-reference-help" class="field-help">Must be unique for this purchase order.</small>
                        </div>
                        <div class="form-field">
                            <label for="invoice-amount">Invoice amount</label>
                            <input id="invoice-amount" name="invoiceAmount" type="number" min="0" step="0.01" required value="${fn:escapeXml(receiptData.invoiceAmount)}">
                        </div>
                        <div class="form-field">
                            <label for="arrived-at">Delivery arrival date and time</label>
                            <input id="arrived-at" name="arrivedAt" type="datetime-local" required value="${fn:escapeXml(receiptData.arrivedAt)}">
                        </div>
                        <div class="form-field">
                            <label for="verified-by">Verified by</label>
                            <input id="verified-by" name="verifiedBy" type="text" maxlength="100" required autocomplete="name" value="${fn:escapeXml(receiptData.verifiedBy)}" aria-describedby="verified-by-help">
                            <small id="verified-by-help" class="field-help">The schema stores this name; the academic MVP does not provide user accounts.</small>
                        </div>
                        <div class="form-field form-field-wide">
                            <label for="receipt-notes">Receipt notes <span class="optional-label">Optional</span></label>
                            <textarea id="receipt-notes" name="notes" rows="3" maxlength="500"><c:out value="${receiptData.notes}" /></textarea>
                        </div>
                    </div>

                    <div class="receipt-items-section">
                        <div class="panel-heading receipt-items-heading">
                            <div>
                                <p class="eyebrow">Line quantities</p>
                                <h3>Items still due</h3>
                            </div>
                            <span class="unit-tag">Accepted goods update stock</span>
                        </div>
                        <p class="panel-note">Short quantity is the outstanding order quantity after this delivery. Rejected quantity is delivered but excluded from inventory.</p>
                        <div class="table-wrap" role="region" tabindex="0" aria-label="Outstanding items and receipt quantity fields; scroll horizontally to see all columns">
                            <table class="receipt-items-table">
                                <caption class="visually-hidden">Quantities ordered, previously received, remaining, and recorded in this receipt</caption>
                                <thead>
                                    <tr>
                                        <th scope="col">Ingredient</th>
                                        <th scope="col">Ordered</th>
                                        <th scope="col">Received before</th>
                                        <th scope="col">Remaining before receipt</th>
                                        <th scope="col">Received now</th>
                                        <th scope="col">Accepted now</th>
                                        <th scope="col">Rejected now</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:choose>
                                        <c:when test="${empty receiptItems}">
                                            <tr><td colspan="7" class="empty-state">This order has no item quantities left to receive.</td></tr>
                                        </c:when>
                                        <c:otherwise>
                                            <c:forEach var="item" items="${receiptItems}" varStatus="rowStatus">
                                                <tr>
                                                    <th scope="row">
                                                        <c:out value="${item.ingredientName}" />
                                                        <span class="sub-cell"><c:out value="${item.baseUnit}" /></span>
                                                        <input type="hidden" name="poItemId" value="${item.poItemId}">
                                                    </th>
                                                    <td><fmt:formatNumber value="${item.orderedQty}" minFractionDigits="0" maxFractionDigits="3" /> <c:out value="${item.baseUnit}" /></td>
                                                    <td><fmt:formatNumber value="${item.receivedToDate}" minFractionDigits="0" maxFractionDigits="3" /> <c:out value="${item.baseUnit}" /></td>
                                                    <td><strong><fmt:formatNumber value="${item.remainingQty}" minFractionDigits="0" maxFractionDigits="3" /></strong> <c:out value="${item.baseUnit}" /></td>
                                                    <td>
                                                        <label class="visually-hidden" for="received-${rowStatus.index}">Received quantity for <c:out value="${item.ingredientName}" /></label>
                                                        <input class="quantity-input" id="received-${rowStatus.index}" name="receivedQty" type="number" min="0" max="${item.remainingQty}" step="0.001" required value="${fn:escapeXml(item.receivedQty)}">
                                                    </td>
                                                    <td>
                                                        <label class="visually-hidden" for="accepted-${rowStatus.index}">Accepted quantity for <c:out value="${item.ingredientName}" /></label>
                                                        <input class="quantity-input" id="accepted-${rowStatus.index}" name="acceptedQty" type="number" min="0" step="0.001" required value="${fn:escapeXml(item.acceptedQty)}">
                                                    </td>
                                                    <td>
                                                        <label class="visually-hidden" for="rejected-${rowStatus.index}">Rejected quantity for <c:out value="${item.ingredientName}" /></label>
                                                        <input class="quantity-input" id="rejected-${rowStatus.index}" name="rejectedQty" type="number" min="0" step="0.001" required value="${fn:escapeXml(item.rejectedQty)}">
                                                    </td>
                                                </tr>
                                            </c:forEach>
                                        </c:otherwise>
                                    </c:choose>
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div class="form-actions">
                        <button class="button button-primary" type="submit" <c:if test="${empty receiptItems}">disabled</c:if>>Save and post accepted stock</button>
                    </div>
                </form>
            </section>
            </c:when>
            <c:otherwise>
                <div class="notice notice-info" role="note">Receipt recording is restricted to administrator accounts.</div>
            </c:otherwise>
            </c:choose>
        </c:if>

        <footer class="page-footer">
            <p>Received quantities determine order completion; rejected goods do not enter inventory.</p>
            <p>MessAudit · Procurement &amp; Operations Management</p>
        </footer>
    </main>
</body>
</html>
