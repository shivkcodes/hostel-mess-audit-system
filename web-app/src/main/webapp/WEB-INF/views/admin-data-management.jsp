<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Admin data management | MessAudit</title>
            <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>
    <main class="page-shell admin-page">
        <section class="page-heading" aria-labelledby="page-title">
            <div><p class="eyebrow">Administrator workspace</p><h1 id="page-title">Data management</h1>
                <p class="intro">Review stored records and apply only the operations supported by their history and relationships.</p></div>
            <a class="button button-secondary" href="${pageContext.request.contextPath}/admin/data-cleanup">Review cleanup options</a>
        </section>
        <c:if test="${not empty successMessage}"><div class="notice notice-success" role="status"><c:out value="${successMessage}" /></div></c:if>
        <c:if test="${not empty adminError}"><div class="notice notice-error" role="alert"><c:out value="${adminError}" /></div></c:if>
        <c:if test="${not adminDataAvailable}"><div class="notice notice-error" role="alert">Administrative data is currently unavailable.</div></c:if>

        <c:if test="${adminDataAvailable}">
            <nav class="admin-category-nav" aria-label="Data management categories">
                <a href="${pageContext.request.contextPath}/admin/data-management?section=reference" class="${adminSection eq 'reference' ? 'is-selected' : ''}" aria-current="${adminSection eq 'reference' ? 'page' : 'false'}">Reference Data</a>
                <a href="${pageContext.request.contextPath}/admin/data-management?section=orders" class="${adminSection eq 'orders' ? 'is-selected' : ''}" aria-current="${adminSection eq 'orders' ? 'page' : 'false'}">Purchase Orders</a>
                <a href="${pageContext.request.contextPath}/admin/data-management?section=receipts" class="${adminSection eq 'receipts' ? 'is-selected' : ''}" aria-current="${adminSection eq 'receipts' ? 'page' : 'false'}">Goods Receipts</a>
                <a href="${pageContext.request.contextPath}/admin/data-management?section=daily" class="${adminSection eq 'daily' ? 'is-selected' : ''}" aria-current="${adminSection eq 'daily' ? 'page' : 'false'}">Daily Mess Records</a>
                <a href="${pageContext.request.contextPath}/admin/data-management?section=accounts" class="${adminSection eq 'accounts' ? 'is-selected' : ''}" aria-current="${adminSection eq 'accounts' ? 'page' : 'false'}">User Accounts</a>
                <a href="${pageContext.request.contextPath}/admin/data-management?section=inventory" class="${adminSection eq 'inventory' ? 'is-selected' : ''}" aria-current="${adminSection eq 'inventory' ? 'page' : 'false'}">Inventory Balances</a>
                <a href="${pageContext.request.contextPath}/admin/data-management?section=ledger" class="${adminSection eq 'ledger' ? 'is-selected' : ''}" aria-current="${adminSection eq 'ledger' ? 'page' : 'false'}">Inventory Ledger</a>
                <a href="${pageContext.request.contextPath}/admin/data-management?section=contracts" class="${adminSection eq 'contracts' ? 'is-selected' : ''}" aria-current="${adminSection eq 'contracts' ? 'page' : 'false'}">Contracts / Reviews</a>
                <a href="${pageContext.request.contextPath}/admin/data-management?section=audit" class="${adminSection eq 'audit' ? 'is-selected' : ''}" aria-current="${adminSection eq 'audit' ? 'page' : 'false'}">Audit History</a>
            </nav>

            <form class="reference-search-form admin-search admin-category-search" action="${pageContext.request.contextPath}/admin/data-management" method="get">
                <input type="hidden" name="section" value="<c:out value='${adminSection}' />">
                <div class="form-field"><label for="admin-search">Search within <c:out value="${adminSectionLabel}" /></label>
                    <input id="admin-search" name="q" type="search" maxlength="80" value="<c:out value='${adminSearch}' />" autocomplete="off" placeholder="<c:out value='${adminSearchPlaceholder}' />" data-admin-search-input></div>
                <div class="reference-search-actions"><button class="button button-primary" type="submit">Search</button><a class="button button-secondary" href="${pageContext.request.contextPath}/admin/data-management?section=<c:out value='${adminSection}' />">Clear</a></div>
                <p class="filter-count" aria-live="polite" data-admin-search-status></p>
            </form>
            <p class="panel-note">This workspace loads one category at a time. Recent result limits and transaction safeguards are unchanged.</p>

            <c:if test="${adminSection eq 'reference'}">
                <section class="panel admin-section admin-reference-section" aria-labelledby="reference-title" data-admin-search-scope>
                    <div class="panel-heading"><div><p class="eyebrow">Validated directories</p><h2 id="reference-title">Reference Data</h2></div></div>
                    <p class="panel-note">Open the existing validated management page for the record type you need. Those pages retain their validation, dependency checks, and safe delete rules.</p>
                    <div class="admin-reference-grid">
                        <a class="admin-reference-entry" data-admin-search-row href="${pageContext.request.contextPath}/vendors"><span class="admin-reference-icon" aria-hidden="true">V</span><span><strong>Vendors</strong><small>Search, view, add, edit, deactivate, and dependency-check deletion</small></span><span aria-hidden="true">→</span></a>
                        <a class="admin-reference-entry" data-admin-search-row href="${pageContext.request.contextPath}/ingredients"><span class="admin-reference-icon" aria-hidden="true">I</span><span><strong>Ingredients</strong><small>Search and manage ingredient details while protecting historical units</small></span><span aria-hidden="true">→</span></a>
                        <a class="admin-reference-entry" data-admin-search-row href="${pageContext.request.contextPath}/mess-blocks"><span class="admin-reference-icon" aria-hidden="true">B</span><span><strong>Mess Blocks</strong><small>Search, manage, and check historical dependencies before deletion</small></span><span aria-hidden="true">→</span></a>
                    </div>
                </section>
            </c:if>

            <c:if test="${adminSection eq 'orders'}">
            <section class="panel admin-section" id="orders" aria-labelledby="orders-title" data-admin-search-scope>
                <div class="panel-heading"><div><p class="eyebrow">Procurement history</p><h2 id="orders-title">Purchase orders and items</h2></div><div class="admin-section-actions"><a class="text-link" href="${pageContext.request.contextPath}/procurement">Create purchase order</a><a class="text-link" href="${pageContext.request.contextPath}/admin/data-cleanup?section=orders#orders">Review safe cleanup</a></div></div>
                <p class="panel-note">Orders with receipt, review, or audit history are preserved. Open or partial orders can be cancelled with a reason; eligible unreceived orders can be reviewed for individual deletion.</p>
                <div class="table-wrap" role="region" tabindex="0" aria-label="Purchase orders">
                    <table class="admin-table"><caption class="visually-hidden">Purchase orders and dependency counts</caption><thead><tr><th>Order</th><th>Vendor</th><th>Mess block</th><th>Order date</th><th>Status</th><th>Items</th><th>Receipts</th><th>Reviews / audit</th><th>Details</th></tr></thead><tbody>
                    <c:forEach items="${adminOrders}" var="row"><tr>
                        <td><strong><c:out value="${row.po_number}" /></strong></td><td><c:out value="${row.vendor_name}" /></td><td><c:out value="${row.block_name}" /></td>
                        <td><fmt:formatDate value="${row.ordered_at}" pattern="yyyy-MM-dd" /></td><td><span class="status-chip"><c:out value="${row.status}" /></span></td>
                        <td><c:out value="${row.item_count}" /></td><td><c:out value="${row.receipt_count}" /></td><td><c:out value="${row.review_count}" /> / <c:out value="${row.audit_count}" /></td>
                        <td><a href="${pageContext.request.contextPath}/admin/data-management?section=orders&orderId=${row.po_id}#order-detail">View lines</a> · <a href="${pageContext.request.contextPath}/admin/data-cleanup?section=orders&orderId=${row.po_id}#order-review">Review</a></td>
                    </tr></c:forEach><c:if test="${empty adminOrders}"><tr><td colspan="9" class="empty-cell">No purchase orders match this search.</td></tr></c:if>
                    </tbody></table>
                </div>
                <c:if test="${not empty selectedOrder}">
                    <div class="admin-detail" id="order-detail"><div class="panel-heading"><div><p class="eyebrow">Order detail</p><h3><c:out value="${selectedOrder.po_number}" /> · <c:out value="${selectedOrder.status}" /></h3></div><a href="${pageContext.request.contextPath}/admin/data-management?section=orders#orders">Close</a></div>
                    <dl class="admin-detail-facts"><div><dt>Vendor</dt><dd><c:out value="${selectedOrder.vendor_name}" /></dd></div><div><dt>Block</dt><dd><c:out value="${selectedOrder.block_name}" /></dd></div><div><dt>Expected by</dt><dd><c:choose><c:when test="${not empty selectedOrder.expected_by}"><fmt:formatDate value="${selectedOrder.expected_by}" pattern="yyyy-MM-dd" /></c:when><c:otherwise>Not recorded</c:otherwise></c:choose></dd></div><div><dt>Approved amount</dt><dd><fmt:formatNumber value="${selectedOrder.approved_amount}" minFractionDigits="2" maxFractionDigits="2" /></dd></div></dl>
                    <div class="table-wrap"><table class="admin-table"><thead><tr><th>Ingredient</th><th>Ordered</th><th>Received</th><th>Accepted</th><th>Rejected</th><th>Unit price</th></tr></thead><tbody><c:forEach items="${selectedOrderItems}" var="line"><tr><td><c:out value="${line.ingredient_name}" /></td><td><c:out value="${line.ordered_qty}" /> <c:out value="${line.base_unit}" /></td><td><c:out value="${line.received_qty}" /></td><td><c:out value="${line.accepted_qty}" /></td><td><c:out value="${line.rejected_qty}" /></td><td><fmt:formatNumber value="${line.unit_price}" minFractionDigits="2" maxFractionDigits="2" /></td></tr></c:forEach><c:if test="${empty selectedOrderItems}"><tr><td colspan="6" class="empty-cell">No order items are recorded.</td></tr></c:if></tbody></table></div>
                    </div>
                </c:if>
            </section>
            </c:if>

            <c:if test="${adminSection eq 'receipts'}">
            <section class="panel admin-section" id="receipts" aria-labelledby="receipts-title" data-admin-search-scope>
                <div class="panel-heading"><div><p class="eyebrow">Delivery evidence</p><h2 id="receipts-title">Goods receipts and receipt items</h2></div><a class="text-link" href="${pageContext.request.contextPath}/admin/data-cleanup?section=receipts#receipts">Review receipt cleanup</a></div>
                <p class="panel-note">Verified receipts are historical evidence. If accepted stock was posted, use a reasoned compensating reversal; original receipt and ledger rows remain intact. <a href="${pageContext.request.contextPath}/goods-receipts">Open the supported receipt workflow</a>.</p>
                <div class="table-wrap" role="region" tabindex="0" aria-label="Goods receipts"><table class="admin-table"><thead><tr><th>Receipt</th><th>Purchase order</th><th>Vendor</th><th>Arrived</th><th>Received</th><th>Accepted</th><th>Rejected</th><th>Inventory</th><th>Evidence</th></tr></thead><tbody>
                    <c:forEach items="${adminReceipts}" var="row"><tr><td>#<c:out value="${row.receipt_id}" /></td><td><c:out value="${row.po_number}" /></td><td><c:out value="${row.vendor_name}" /></td><td><fmt:formatDate value="${row.arrived_at}" pattern="yyyy-MM-dd HH:mm" /></td><td><c:out value="${row.received_qty}" /></td><td><c:out value="${row.accepted_qty}" /></td><td><c:out value="${row.rejected_qty}" /></td><td><c:choose><c:when test="${not empty row.inventory_processed_at}">Posted</c:when><c:otherwise>Not posted</c:otherwise></c:choose></td><td><a href="${pageContext.request.contextPath}/admin/data-management?section=receipts&receiptId=${row.receipt_id}#receipt-detail">View</a> · <a href="${pageContext.request.contextPath}/admin/data-cleanup?section=receipts&receiptId=${row.receipt_id}#receipt-review">Review</a></td></tr></c:forEach>
                    <c:if test="${empty adminReceipts}"><tr><td colspan="9" class="empty-cell">No receipts match this search.</td></tr></c:if></tbody></table></div>
                <c:if test="${not empty selectedReceipt}"><div class="admin-detail" id="receipt-detail"><div class="panel-heading"><div><p class="eyebrow">Receipt detail</p><h3>Receipt #<c:out value="${selectedReceipt.receipt_id}" /> · <c:out value="${selectedReceipt.po_number}" /></h3></div><span class="status-chip"><c:choose><c:when test="${selectedReceipt.reversed_flag eq 'Y'}">Reversed</c:when><c:when test="${not empty selectedReceipt.inventory_processed_at}">Inventory posted</c:when><c:otherwise>Not processed</c:otherwise></c:choose></span></div><dl class="admin-detail-facts"><div><dt>Vendor</dt><dd><c:out value="${selectedReceipt.vendor_name}" /></dd></div><div><dt>Mess block</dt><dd><c:out value="${selectedReceipt.block_name}" /></dd></div><div><dt>Invoice reference</dt><dd><c:out value="${selectedReceipt.invoice_reference}" /></dd></div><div><dt>Invoice amount</dt><dd><fmt:formatNumber value="${selectedReceipt.invoice_amount}" minFractionDigits="2" maxFractionDigits="2" /></dd></div><div><dt>Verified</dt><dd><c:choose><c:when test="${not empty selectedReceipt.verified_at}"><fmt:formatDate value="${selectedReceipt.verified_at}" pattern="yyyy-MM-dd HH:mm" /></c:when><c:otherwise>Not verified</c:otherwise></c:choose></dd></div></dl><div class="table-wrap"><table class="admin-table"><thead><tr><th>Ingredient</th><th>Received</th><th>Accepted</th><th>Rejected</th><th>Notes</th></tr></thead><tbody><c:forEach items="${selectedReceiptItems}" var="line"><tr><td><c:out value="${line.ingredient_name}" /> (<c:out value="${line.base_unit}" />)</td><td><c:out value="${line.received_qty}" /></td><td><c:out value="${line.accepted_qty}" /></td><td><c:out value="${line.rejected_qty}" /></td><td><c:out value="${line.notes}" /></td></tr></c:forEach></tbody></table></div></div></c:if>
            </section>
            </c:if>

            <c:if test="${adminSection eq 'daily'}">
            <section class="panel admin-section" id="daily-records" aria-labelledby="daily-title" data-admin-search-scope>
                <div class="panel-heading"><div><p class="eyebrow">Mess operations</p><h2 id="daily-title">Daily meals and measurements</h2></div><a class="button button-primary button-small" href="${pageContext.request.contextPath}/admin/data-management#daily-form">Add daily record</a></div>
                <p class="panel-note">Blank measurements remain NULL. Leftovers are reported as measured leftovers, not as confirmed disposal.</p>
                <div class="table-wrap"><table class="admin-table"><thead><tr><th>Date</th><th>Block</th><th>Meals</th><th>Prepared kg</th><th>Measured leftovers kg</th><th>Measurement method</th><th>Actions</th></tr></thead><tbody><c:forEach items="${adminDailyRecords}" var="row"><tr><td><fmt:formatDate value="${row.record_date}" pattern="yyyy-MM-dd" /></td><td><c:out value="${row.block_name}" /></td><td><c:out value="${row.meals_served}" /></td><td><c:choose><c:when test="${not empty row.prepared_kg}"><c:out value="${row.prepared_kg}" /></c:when><c:otherwise>Not measured</c:otherwise></c:choose></td><td><c:choose><c:when test="${not empty row.leftover_kg}"><c:out value="${row.leftover_kg}" /></c:when><c:otherwise>Not measured</c:otherwise></c:choose></td><td><c:out value="${row.measurement_method}" /></td><td><a href="${pageContext.request.contextPath}/admin/data-management?section=daily&recordId=${row.record_id}#daily-form">Edit</a> · <a href="${pageContext.request.contextPath}/admin/data-cleanup?section=daily&recordId=${row.record_id}#daily-review">Delete review</a></td></tr></c:forEach><c:if test="${empty adminDailyRecords}"><tr><td colspan="7" class="empty-cell">No daily records match this search.</td></tr></c:if></tbody></table></div>
                <c:if test="${not empty selectedDailyRecord}"><c:set var="dailyFormHeading" value="Edit daily record" /></c:if><c:if test="${empty selectedDailyRecord}"><c:set var="dailyFormHeading" value="Add a daily record" /></c:if>
                <form class="panel admin-inline-form" id="daily-form" action="${pageContext.request.contextPath}/admin/data-management" method="post">
                    <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />"><input type="hidden" name="section" value="daily"><input type="hidden" name="action" value="save-daily">
                    <c:if test="${not empty selectedDailyRecord}"><input type="hidden" name="recordId" value="${selectedDailyRecord.record_id}"></c:if>
                    <div class="panel-heading"><div><p class="eyebrow">Controlled edit</p><h3><c:out value="${dailyFormHeading}" /></h3></div></div>
                    <div class="reference-management-form">
                        <div class="form-field"><label for="daily-block">Mess block</label><select id="daily-block" name="blockId" required><option value="">Choose a block</option><c:forEach items="${adminBlocks}" var="block"><option value="${block.block_id}" <c:if test="${not empty selectedDailyRecord and selectedDailyRecord.block_id eq block.block_id}">selected</c:if>><c:out value="${block.block_name}" /></option></c:forEach></select></div>
                        <div class="form-field"><label for="daily-date">Record date</label><input id="daily-date" type="date" name="recordDate" required value="<c:out value='${selectedDailyRecord.record_date}' />"></div>
                        <div class="form-field"><label for="daily-meals">Meals served</label><input id="daily-meals" type="number" name="mealsServed" min="0" max="99999999" step="1" required value="<c:out value='${selectedDailyRecord.meals_served}' />"></div>
                        <div class="form-field"><label for="daily-prepared">Prepared quantity (kg, optional)</label><input id="daily-prepared" type="number" name="preparedKg" min="0" max="999999999" step="0.001" value="<c:out value='${selectedDailyRecord.prepared_kg}' />"></div>
                        <div class="form-field"><label for="daily-leftover">Measured leftovers (kg, optional)</label><input id="daily-leftover" type="number" name="leftoverKg" min="0" max="999999999" step="0.001" value="<c:out value='${selectedDailyRecord.leftover_kg}' />"><small>Keep this empty when the measurement is missing. Zero means it was measured as zero.</small></div>
                        <div class="form-field"><label for="daily-method">Measurement method</label><input id="daily-method" type="text" name="measurementMethod" maxlength="200" value="<c:out value='${selectedDailyRecord.measurement_method}' />"></div>
                        <div class="form-field admin-form-wide"><label for="daily-notes">Notes</label><textarea id="daily-notes" name="notes" maxlength="500" rows="3"><c:out value="${selectedDailyRecord.notes}" /></textarea></div>
                        <div class="reference-form-actions"><button class="button button-primary" type="submit">Save daily record</button><c:if test="${not empty selectedDailyRecord}"><a class="button button-secondary" href="${pageContext.request.contextPath}/admin/data-management?section=daily#daily-records">Cancel edit</a></c:if></div>
                    </div>
                </form>
            </section>
            </c:if>

            <c:if test="${adminSection eq 'accounts'}">
            <section class="panel admin-section" id="accounts" aria-labelledby="accounts-title" data-admin-search-scope>
                <div class="panel-heading"><div><p class="eyebrow">Access controls</p><h2 id="accounts-title">Application accounts</h2></div><span class="unit-tag">No passwords or hashes shown</span></div>
                <p class="panel-note">Role and active-state updates are serialized in a transaction. The current administrator cannot change their own access and at least one active ADMIN is required. Accounts are deactivated rather than hard-deleted so audit attribution remains intact.</p>
                
                <div class="table-wrap"><table class="admin-table" id="accounts-table"><thead><tr><th>Username</th><th>Role</th><th>Status</th><th>Created</th><th>Access action</th></tr></thead><tbody><c:forEach items="${adminUsers}" var="user"><tr><td><strong><a href="${pageContext.request.contextPath}/admin/data-management?section=accounts&userId=${user.user_id}#account-detail"><c:out value="${user.username}" /></a></strong></td><td><c:out value="${user.role_code}" /></td><td><c:choose><c:when test="${user.active_flag eq 'Y'}">Active</c:when><c:otherwise>Inactive</c:otherwise></c:choose></td><td><fmt:formatDate value="${user.created_at}" pattern="yyyy-MM-dd" /></td><td><form class="admin-user-form" action="${pageContext.request.contextPath}/admin/data-management" method="post"><input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />"><input type="hidden" name="section" value="accounts"><input type="hidden" name="action" value="update-user"><input type="hidden" name="userId" value="${user.user_id}"><label class="visually-hidden" for="role-${user.user_id}">Role for <c:out value="${user.username}" /></label><select id="role-${user.user_id}" name="role"><option value="READ_ONLY" <c:if test="${user.role_code eq 'READ_ONLY'}">selected</c:if>>READ_ONLY</option><option value="ADMIN" <c:if test="${user.role_code eq 'ADMIN'}">selected</c:if>>ADMIN</option></select><label class="visually-hidden" for="active-${user.user_id}">Status for <c:out value="${user.username}" /></label><select id="active-${user.user_id}" name="active"><option value="Y" <c:if test="${user.active_flag eq 'Y'}">selected</c:if>>Active</option><option value="N" <c:if test="${user.active_flag eq 'N'}">selected</c:if>>Inactive</option></select><label class="confirmation-check"><input type="checkbox" name="confirm" value="yes" required><span>Confirm this access change</span></label><button class="button button-secondary button-small" type="submit">Update</button></form></td></tr></c:forEach><c:if test="${empty adminUsers}"><tr><td colspan="5" class="empty-cell">No accounts match this search.</td></tr></c:if></tbody></table></div>
                <c:if test="${not empty selectedAdminUser}"><div class="admin-detail" id="account-detail"><div class="panel-heading"><div><p class="eyebrow">Account detail</p><h3><c:out value="${selectedAdminUser.username}" /></h3></div><a href="${pageContext.request.contextPath}/admin/data-management?section=accounts#accounts">Close</a></div><dl class="admin-detail-facts"><div><dt>Account ID</dt><dd><c:out value="${selectedAdminUser.user_id}" /></dd></div><div><dt>Role</dt><dd><c:out value="${selectedAdminUser.role_code}" /></dd></div><div><dt>Status</dt><dd><c:choose><c:when test="${selectedAdminUser.active_flag eq 'Y'}">Active</c:when><c:otherwise>Inactive</c:otherwise></c:choose></dd></div><div><dt>Created</dt><dd><fmt:formatDate value="${selectedAdminUser.created_at}" pattern="yyyy-MM-dd HH:mm" /></dd></div><div><dt>Password last changed</dt><dd><fmt:formatDate value="${selectedAdminUser.password_changed_at}" pattern="yyyy-MM-dd HH:mm" /></dd></div></dl><p class="panel-note">No password hash or personal profile fields are shown. Use the account row to change role or active status; permanent deletion is not offered.</p></div></c:if>
                <c:if test="${not empty param.userId and empty selectedAdminUser}"><p class="notice notice-info" id="account-detail">The selected account does not exist or its ID is invalid.</p></c:if>
            </section>
            </c:if>

            <c:if test="${adminSection eq 'inventory'}">
            <section class="panel admin-section" id="inventory" aria-labelledby="inventory-title" data-admin-search-scope>
                <div class="panel-heading"><div><p class="eyebrow">Stock view</p><h2 id="inventory-title">Inventory balances</h2></div><span class="unit-tag">Read only</span></div>
                <p class="panel-note">Balances are maintained by receipt/stock workflows and are not editable here. <a href="${pageContext.request.contextPath}/inventory">Open inventory management</a> · <a href="${pageContext.request.contextPath}/admin/data-cleanup?section=receipts#receipts">Review an eligible receipt reversal</a>.</p>
                
                <div class="table-wrap"><table class="admin-table" id="inventory-table"><thead><tr><th>Block</th><th>Ingredient</th><th>On hand</th><th>Reorder level</th><th>Updated</th></tr></thead><tbody><c:forEach items="${adminInventory}" var="row"><tr><td><c:out value="${row.block_name}" /></td><td><c:out value="${row.ingredient_name}" /> <span class="unit-tag"><c:out value="${row.base_unit}" /></span></td><td><c:out value="${row.on_hand_qty}" /></td><td><c:out value="${row.reorder_level}" /></td><td><fmt:formatDate value="${row.updated_at}" pattern="yyyy-MM-dd HH:mm" /></td></tr></c:forEach><c:if test="${empty adminInventory}"><tr><td colspan="5" class="empty-cell">No inventory balances are recorded.</td></tr></c:if></tbody></table></div>
                
            </section>
            </c:if>

            <c:if test="${adminSection eq 'ledger'}">
            <section class="panel admin-section" id="inventory-ledger" aria-labelledby="ledger-title" data-admin-search-scope>
                <div class="panel-heading"><div><p class="eyebrow">Stock movement evidence</p><h2 id="ledger-title">Inventory transaction ledger</h2></div><span class="unit-tag">Read only</span></div>
                <p class="panel-note">Ledger rows cannot be edited or deleted here. Supported receipt correction workflows preserve original transaction evidence.</p>
                <div class="table-wrap"><table class="admin-table"><thead><tr><th>Reference</th><th>Block</th><th>Ingredient</th><th>Type</th><th>Quantity change</th><th>Balance after</th><th>Created</th><th>Notes</th></tr></thead><tbody><c:forEach items="${adminTransactions}" var="row"><tr><td><c:out value="${row.transaction_reference}" /></td><td><c:out value="${row.block_name}" /></td><td><c:out value="${row.ingredient_name}" /> (<c:out value="${row.base_unit}" />)</td><td><c:out value="${row.transaction_type}" /></td><td><c:out value="${row.quantity_delta}" /></td><td><c:out value="${row.balance_after_qty}" /></td><td><fmt:formatDate value="${row.created_at}" pattern="yyyy-MM-dd HH:mm" /></td><td><c:out value="${row.notes}" /></td></tr></c:forEach><c:if test="${empty adminTransactions}"><tr><td colspan="8" class="empty-cell">No inventory transactions are recorded.</td></tr></c:if></tbody></table></div>
            </section>
            </c:if>

            <c:if test="${adminSection eq 'contracts'}">
            <section class="panel admin-section" id="contract-evidence" aria-labelledby="contracts-title" data-admin-search-scope>
                <div class="panel-heading"><div><p class="eyebrow">Vendor terms</p><h2 id="contracts-title">Contract rules and penalty reviews</h2></div><span class="unit-tag">Read only</span></div>
                <p class="panel-note">Contract terms and reviewed calculations are shown as evidence. This screen does not invent or edit penalty rules.</p>
                <h3 id="contract-rules">Contract rules</h3><div class="table-wrap"><table class="admin-table" id="contracts-table"><thead><tr><th>Vendor</th><th>Rule</th><th>Discrepancy</th><th>Effective</th><th>Tolerance</th><th>Penalty</th><th>Terms</th></tr></thead><tbody><c:forEach items="${adminContractRules}" var="row"><tr><td><c:out value="${row.vendor_name}" /></td><td><c:out value="${row.rule_code}" /> · <c:out value="${row.rule_name}" /></td><td><c:out value="${row.discrepancy_type}" /></td><td><fmt:formatDate value="${row.effective_from}" pattern="yyyy-MM-dd" /> – <c:choose><c:when test="${not empty row.effective_to}"><fmt:formatDate value="${row.effective_to}" pattern="yyyy-MM-dd" /></c:when><c:otherwise>open</c:otherwise></c:choose></td><td><c:out value="${row.tolerance_percent}" />%</td><td><c:out value="${row.penalty_percent}" />%</td><td><c:out value="${row.terms_text}" /></td></tr></c:forEach><c:if test="${empty adminContractRules}"><tr><td colspan="7" class="empty-cell">No contract rules are recorded.</td></tr></c:if></tbody></table></div>
                <h3 class="admin-subheading" id="penalty-reviews">Penalty reviews</h3><div class="table-wrap"><table class="admin-table" id="reviews-table"><thead><tr><th>Order</th><th>Vendor</th><th>Rule</th><th>Eligible lines</th><th>Eligible value</th><th>Proposed</th><th>Approved</th><th>Status</th><th>Calculation basis</th></tr></thead><tbody><c:forEach items="${adminPenaltyReviews}" var="row"><tr><td><c:out value="${row.po_number}" /></td><td><c:out value="${row.vendor_name}" /></td><td><c:out value="${row.rule_code}" /></td><td><c:out value="${row.eligible_line_count}" /></td><td><fmt:formatNumber value="${row.eligible_value}" minFractionDigits="2" maxFractionDigits="2" /></td><td><fmt:formatNumber value="${row.proposed_amount}" minFractionDigits="2" maxFractionDigits="2" /></td><td><c:choose><c:when test="${not empty row.approved_amount}"><fmt:formatNumber value="${row.approved_amount}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise>Not approved</c:otherwise></c:choose></td><td><c:out value="${row.status}" /></td><td><c:out value="${row.calculation_basis}" /></td></tr></c:forEach><c:if test="${empty adminPenaltyReviews}"><tr><td colspan="9" class="empty-cell">No penalty reviews are recorded.</td></tr></c:if></tbody></table></div>
            </section>
            </c:if>

            <c:if test="${adminSection eq 'audit'}">
            <section class="panel admin-section" id="audit-history" aria-labelledby="audit-title" data-admin-search-scope>
                <div class="panel-heading"><div><p class="eyebrow">Change traceability</p><h2 id="audit-title">Recent audit events</h2></div><span class="unit-tag">Read only</span></div>
                <p class="panel-note">This view shows event metadata only. Audit rows are not editable from the web application; old/new payloads are not displayed.</p>
                <div class="table-wrap"><table class="admin-table" id="audit-table"><thead><tr><th>Audit ID</th><th>Entity</th><th>Record key</th><th>Operation</th><th>Changed at</th><th>Actor</th></tr></thead><tbody><c:forEach items="${adminAuditEvents}" var="row"><tr><td><c:out value="${row.audit_id}" /></td><td><c:out value="${row.table_name}" /></td><td><c:out value="${row.record_key}" /></td><td><c:out value="${row.operation_type}" /></td><td><fmt:formatDate value="${row.changed_at}" pattern="yyyy-MM-dd HH:mm:ss" /></td><td><c:out value="${row.changed_by}" /></td></tr></c:forEach><c:if test="${empty adminAuditEvents}"><tr><td colspan="6" class="empty-cell">No audit events are recorded.</td></tr></c:if></tbody></table></div>
            </section>
            </c:if>
        </c:if>
    </main>
    <script src="${pageContext.request.contextPath}/js/admin-data-filter.js?v=20261029-focused" defer></script>
</body>
</html>
