<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${directoryLabel}" /> | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>

    <main class="page-shell">
        <section class="page-heading" aria-labelledby="page-title">
            <div>
                <p class="eyebrow">Reference data</p>
                <h1 id="page-title"><c:out value="${directoryLabel}" /></h1>
                <p class="intro">Search and review reference records. Administrators can manage records while preserving operational history.</p>
            </div>
            <c:choose>
                <c:when test="${canManage}">
                    <a class="button button-primary" href="${pageContext.request.contextPath}${directoryPath}?new=1">Add <c:out value="${directorySingular}" /></a>
                </c:when>
                <c:otherwise><span class="unit-tag">Read only</span></c:otherwise>
            </c:choose>
        </section>

        <nav class="reference-tabs" aria-label="Reference data directories">
            <a href="${pageContext.request.contextPath}/vendors" aria-current="${directoryPath eq '/vendors' ? 'page' : 'false'}">Vendors</a>
            <a href="${pageContext.request.contextPath}/ingredients" aria-current="${directoryPath eq '/ingredients' ? 'page' : 'false'}">Ingredients</a>
            <a href="${pageContext.request.contextPath}/mess-blocks" aria-current="${directoryPath eq '/mess-blocks' ? 'page' : 'false'}">Mess blocks</a>
        </nav>

        <c:if test="${not canManage}">
            <div class="notice notice-info reference-security-note" role="note">You have read-only access. An administrator account is required to change reference data.</div>
        </c:if>
        <c:if test="${not empty mutationNotice}">
            <div class="notice ${mutationNoticeType eq 'success' ? 'notice-success' : 'notice-info'}" role="status"><c:out value="${mutationNotice}" /></div>
        </c:if>
        <c:if test="${not empty inputError}">
            <div class="notice notice-error" role="alert"><c:out value="${inputError}" /></div>
        </c:if>
        <c:if test="${not referenceAvailable}">
            <div class="notice notice-error" role="alert"><c:out value="${referenceError}" /></div>
        </c:if>

        <c:if test="${canManage and referenceAvailable and (createMode or (editMode and not empty selectedRecord))}">
            <section class="panel reference-form-panel" aria-labelledby="reference-form-title">
                <div class="panel-heading">
                    <div>
                        <p class="eyebrow"><c:out value="${createMode ? 'New record' : 'Update record'}" /></p>
                        <h2 id="reference-form-title"><c:choose><c:when test="${createMode}">Add</c:when><c:otherwise>Edit</c:otherwise></c:choose> <c:out value="${directorySingular}" /></h2>
                    </div>
                    <a class="button button-secondary button-small" href="${pageContext.request.contextPath}${directoryPath}">Cancel</a>
                </div>
                <form class="reference-management-form" action="${pageContext.request.contextPath}${directoryPath}" method="post" accept-charset="UTF-8">
                    <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />">
                    <input type="hidden" name="operation" value="${createMode ? 'create' : 'update'}">
                    <c:if test="${editMode}"><input type="hidden" name="recordId" value="${selectedRecord.id}"></c:if>
                    <input type="hidden" name="q" value="<c:out value='${search}' />">
                    <div class="form-field">
                        <label for="reference-code">Code <span aria-hidden="true">*</span></label>
                        <input id="reference-code" name="code" type="text" maxlength="20" value="<c:out value='${formValues.code}' />" autocomplete="off" required>
                    </div>
                    <div class="form-field">
                        <label for="reference-name">Name <span aria-hidden="true">*</span></label>
                        <input id="reference-name" name="name" type="text" maxlength="${directory.path eq '/vendors' ? 120 : 100}" value="<c:out value='${formValues.name}' />" autocomplete="off" required>
                    </div>
                    <c:if test="${directory.path eq '/vendors'}">
                        <div class="form-field">
                            <label for="contact-name">Contact name</label>
                            <input id="contact-name" name="contact_name" type="text" maxlength="100" value="<c:out value='${formValues.contact_name}' />" autocomplete="name">
                        </div>
                        <div class="form-field">
                            <label for="vendor-phone">Phone</label>
                            <input id="vendor-phone" name="phone" type="tel" maxlength="30" value="<c:out value='${formValues.phone}' />" autocomplete="tel">
                        </div>
                        <div class="form-field">
                            <label for="vendor-email">Email</label>
                            <input id="vendor-email" name="email" type="email" maxlength="254" value="<c:out value='${formValues.email}' />" autocomplete="email">
                        </div>
                    </c:if>
                    <c:if test="${directory.path eq '/ingredients'}">
                        <div class="form-field">
                            <label for="base-unit">Base unit <span aria-hidden="true">*</span></label>
                            <select id="base-unit" name="base_unit" required>
                                <option value="KG" <c:if test="${formValues.base_unit eq 'KG'}">selected</c:if>>KG — kilograms</option>
                                <option value="L" <c:if test="${formValues.base_unit eq 'L'}">selected</c:if>>L — litres</option>
                                <option value="EACH" <c:if test="${formValues.base_unit eq 'EACH'}">selected</c:if>>EACH — individual units</option>
                            </select>
                            <small>Existing unit changes are blocked when order or inventory history refers to this ingredient.</small>
                        </div>
                    </c:if>
                    <div class="reference-form-actions">
                        <button class="button button-primary" type="submit"><c:out value="${createMode ? 'Create record' : 'Save changes'}" /></button>
                        <a class="button button-secondary" href="${pageContext.request.contextPath}${directoryPath}">Cancel</a>
                    </div>
                </form>
            </section>
        </c:if>

        <section class="panel reference-filter-panel" aria-labelledby="reference-filter-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Find a record</p><h2 id="reference-filter-title">Search <c:out value="${directoryLabel}" /></h2></div>
            </div>
            <form class="reference-search-form" action="${pageContext.request.contextPath}${directoryPath}" method="get">
                <div class="form-field reference-search-field">
                    <label for="reference-search">Name or code</label>
                    <input id="reference-search" name="q" type="search" maxlength="100" value="<c:out value='${search}' />"
                           placeholder="Search by name or code" autocomplete="off">
                </div>
                <div class="reference-search-actions">
                    <button class="button button-primary" type="submit" <c:if test="${not referenceAvailable}">disabled</c:if>>Search</button>
                    <a class="button button-secondary" href="${pageContext.request.contextPath}${directoryPath}">Clear</a>
                </div>
            </form>
        </section>

        <section class="panel" aria-labelledby="reference-list-title">
            <div class="panel-heading">
                <div><p class="eyebrow">Stored records</p><h2 id="reference-list-title"><c:out value="${directoryLabel}" /> list</h2></div>
                <c:if test="${referenceAvailable}"><span class="unit-tag"><fmt:formatNumber value="${fn:length(directoryRecords)}" groupingUsed="true" /> records shown</span></c:if>
            </div>
            <div class="table-wrap" role="region" tabindex="0" aria-label="Reference data list; scroll horizontally to see all columns">
                <table class="reference-table">
                    <caption class="visually-hidden"><c:out value="${directoryLabel}" /> directory</caption>
                    <thead><tr><th scope="col">Code</th><th scope="col">Name</th><th scope="col">Details</th><th scope="col">Status</th><th scope="col">Created</th><th scope="col">View</th><c:if test="${canManage}"><th scope="col">Manage</th></c:if></tr></thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${not referenceAvailable}"><tr><td colspan="${canManage ? 7 : 6}" class="empty-state">Records are unavailable right now.</td></tr></c:when>
                            <c:when test="${empty directoryRecords}"><tr><td colspan="${canManage ? 7 : 6}" class="empty-state">No records match this search.</td></tr></c:when>
                            <c:otherwise>
                                <c:forEach var="record" items="${directoryRecords}">
                                    <tr>
                                        <td><code><c:out value="${record.code}" /></code></td>
                                        <th scope="row"><c:out value="${record.name}" /></th>
                                        <td>
                                            <c:choose>
                                                <c:when test="${not empty record.attributes}">
                                                    <c:forEach var="attribute" items="${record.attributes}" varStatus="loop"><c:if test="${loop.index gt 0}"> · </c:if><c:out value="${attribute.value}" /></c:forEach>
                                                </c:when>
                                                <c:otherwise>—</c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td><span class="status-pill ${record.activeFlag eq 'Y' ? 'status-open' : 'status-muted'}"><c:out value="${record.activeFlag eq 'Y' ? 'Active' : 'Inactive'}" /></span></td>
                                        <td><fmt:formatDate value="${record.createdAt}" pattern="dd MMM yyyy, HH:mm" /></td>
                                        <td><a class="button button-secondary button-small" href="${pageContext.request.contextPath}${directoryPath}?id=${record.id}">View details</a></td>
                                        <c:if test="${canManage}">
                                            <td class="reference-row-actions">
                                                <a class="button button-secondary button-small" href="${pageContext.request.contextPath}${directoryPath}?id=${record.id}&amp;edit=1">Edit</a>
                                                <form class="inline-management-form" action="${pageContext.request.contextPath}${directoryPath}" method="post">
                                                    <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />">
                                                    <input type="hidden" name="recordId" value="${record.id}">
                                                    <input type="hidden" name="q" value="<c:out value='${search}' />">
                                                    <c:choose>
                                                        <c:when test="${record.activeFlag eq 'Y'}"><input type="hidden" name="operation" value="deactivate"><button class="button button-warning button-small" type="submit">Deactivate</button></c:when>
                                                        <c:otherwise><input type="hidden" name="operation" value="activate"><button class="button button-secondary button-small" type="submit">Reactivate</button></c:otherwise>
                                                    </c:choose>
                                                </form>
                                            </td>
                                        </c:if>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>
        </section>

        <c:if test="${selectedIdProvided and empty selectedRecord and referenceAvailable and empty inputError}">
            <div class="notice notice-warning" role="status">That record was not found. It may have been removed or the ID may no longer be valid.</div>
        </c:if>

        <c:if test="${not empty selectedRecord}">
            <section class="panel reference-detail-panel" aria-labelledby="reference-detail-title">
                <div class="panel-heading">
                    <div><p class="eyebrow">Record details</p><h2 id="reference-detail-title"><c:out value="${selectedRecord.name}" /></h2></div>
                    <span class="status-pill ${selectedRecord.activeFlag eq 'Y' ? 'status-open' : 'status-muted'}"><c:out value="${selectedRecord.activeFlag eq 'Y' ? 'Active' : 'Inactive'}" /></span>
                </div>
                <dl class="reference-attributes">
                    <div><dt>Code</dt><dd><c:out value="${selectedRecord.code}" /></dd></div>
                    <div><dt>Record ID</dt><dd><c:out value="${selectedRecord.id}" /></dd></div>
                    <c:forEach var="attribute" items="${selectedRecord.attributes}">
                        <div><dt><c:out value="${attribute.label}" /></dt><dd><c:out value="${attribute.value}" /></dd></div>
                    </c:forEach>
                    <div><dt>Created</dt><dd><fmt:formatDate value="${selectedRecord.createdAt}" pattern="dd MMM yyyy, HH:mm:ss" /></dd></div>
                </dl>

                <div class="reference-dependencies">
                    <div class="panel-heading"><div><p class="eyebrow">Dependency check</p><h3>Related operational records</h3></div></div>
                    <p class="panel-note">Counts are a current snapshot. The server repeats this check in a locked transaction before deletion; Oracle foreign keys remain the final safeguard.</p>
                    <ul class="dependency-list">
                        <c:forEach var="dependency" items="${dependencies}">
                            <li><span><c:out value="${dependency.label}" /></span><strong><fmt:formatNumber value="${dependency.count}" groupingUsed="true" /></strong></li>
                        </c:forEach>
                    </ul>
                    <c:if test="${canManage}">
                        <div class="reference-detail-actions">
                            <a class="button button-secondary" href="${pageContext.request.contextPath}${directoryPath}?id=${selectedRecord.id}&amp;edit=1">Edit record</a>
                            <c:choose>
                                <c:when test="${confirmDeleteMode and not hasDependencies}">
                                    <section class="delete-confirmation" aria-labelledby="delete-confirm-title">
                                        <h3 id="delete-confirm-title">Confirm permanent deletion</h3>
                                        <p>This permanently removes only this reference-data row. It will be rejected if any related history exists or appears before submission.</p>
                                        <form action="${pageContext.request.contextPath}${directoryPath}" method="post">
                                            <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />">
                                            <input type="hidden" name="operation" value="delete">
                                            <input type="hidden" name="recordId" value="${selectedRecord.id}">
                                            <input type="hidden" name="q" value="<c:out value='${search}' />">
                                            <label class="confirmation-check"><input type="checkbox" name="confirmDelete" value="yes" required> I confirm that this record may be permanently deleted.</label>
                                            <div class="reference-form-actions">
                                                <button class="button button-danger" type="submit">Delete <c:out value="${directorySingular}" /></button>
                                                <a class="button button-secondary" href="${pageContext.request.contextPath}${directoryPath}?id=${selectedRecord.id}">Cancel</a>
                                            </div>
                                        </form>
                                    </section>
                                </c:when>
                                <c:when test="${hasDependencies}">
                                    <div class="notice notice-warning reference-delete-blocked" role="note">This record has related history and cannot be hard-deleted. Use deactivation to prevent it from appearing in new procurement selections.</div>
                                </c:when>
                                <c:otherwise>
                                    <a class="button button-danger button-secondary" href="${pageContext.request.contextPath}${directoryPath}?id=${selectedRecord.id}&amp;confirmDelete=1">Delete record</a>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </c:if>
                </div>
            </section>
        </c:if>
    </main>
</body>
</html>
