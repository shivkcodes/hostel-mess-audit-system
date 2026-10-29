<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>My profile | MessAudit</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
</head>
<body>
    <%@ include file="fragments/app-chrome.jspf" %>
    <main class="page-shell profile-page">
        <section class="page-heading" aria-labelledby="profile-title">
            <div><p class="eyebrow">Personal workspace</p><h1 id="profile-title"><c:choose><c:when test="${profileComplete}">My profile</c:when><c:otherwise>Complete your profile</c:otherwise></c:choose></h1>
                <p class="intro">Keep your personal and academic details current. Your profile is visible only to you.</p></div>
            <c:choose><c:when test="${profileComplete}"><span class="profile-completion-badge profile-complete" role="status">Profile complete</span></c:when><c:otherwise><span class="profile-completion-badge profile-incomplete" role="status">Completion needed</span></c:otherwise></c:choose>
        </section>
        <c:if test="${not empty profileError}"><div class="notice notice-error" role="alert"><c:out value="${profileError}" /></div></c:if>
        <c:if test="${not empty profileMissingFields}">
            <div class="notice notice-info profile-required-fields" role="note" aria-label="Required profile fields still needed">
                <strong>Required details to complete:</strong>
                <ul><c:forEach items="${profileMissingFields}" var="missingField"><li><c:out value="${missingField}" /></li></c:forEach></ul>
            </div>
        </c:if>

        <c:choose>
            <c:when test="${profileMigrationRequired}">
                <section class="panel profile-migration-note"><h2>Profile setup is not ready</h2><p>The profile table is not available yet. An administrator must run the numbered migration before profile details can be stored.</p></section>
            </c:when>
            <c:otherwise>
                <form class="panel profile-form" action="${pageContext.request.contextPath}/profile" method="post">
                    <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />">
                    <section class="profile-form-section" aria-labelledby="personal-information-title">
                        <div class="profile-form-heading"><span class="profile-section-icon profile-icon-person" aria-hidden="true">01</span><div><h2 id="personal-information-title">Personal information</h2><p>Basic details associated with your MessAudit profile.</p></div></div>
                        <div class="form-grid">
                            <div class="form-field"><label for="full-name">Full name <span class="required-label">Required</span></label><input id="full-name" name="fullName" type="text" maxlength="120" autocomplete="name" value="<c:out value='${profileForm.fullName}' />" required></div>
                            <div class="form-field"><label for="email-address">Email address <span class="required-label">Required</span></label><input id="email-address" name="emailAddress" type="email" maxlength="254" autocomplete="email" value="<c:out value='${profileForm.emailAddress}' />" required></div>
                            <div class="form-field"><label for="enrollment-id">Student / enrollment ID <span class="optional-label">Optional</span></label><input id="enrollment-id" name="enrollmentId" type="text" maxlength="80" value="<c:out value='${profileForm.enrollmentId}' />"></div>
                        </div>
                    </section>
                    <section class="profile-form-section" aria-labelledby="contact-information-title">
                        <div class="profile-form-heading"><span class="profile-section-icon profile-icon-contact" aria-hidden="true">02</span><div><h2 id="contact-information-title">Contact information</h2><p>Phone and location details are optional.</p></div></div>
                        <div class="form-grid">
                            <div class="form-field"><label for="phone-number">Phone number <span class="optional-label">Optional</span></label><input id="phone-number" name="phoneNumber" type="tel" maxlength="30" autocomplete="tel" value="<c:out value='${profileForm.phoneNumber}' />"></div>
                            <div class="form-field form-field-wide"><label for="address-line">Address <span class="optional-label">Optional</span></label><input id="address-line" name="addressLine" type="text" maxlength="240" autocomplete="street-address" value="<c:out value='${profileForm.addressLine}' />"></div>
                            <div class="form-field"><label for="city">City <span class="optional-label">Optional</span></label><input id="city" name="city" type="text" maxlength="100" autocomplete="address-level2" value="<c:out value='${profileForm.city}' />"></div>
                            <div class="form-field"><label for="state-name">State <span class="optional-label">Optional</span></label><input id="state-name" name="stateName" type="text" maxlength="100" autocomplete="address-level1" value="<c:out value='${profileForm.stateName}' />"></div>
                            <div class="form-field"><label for="postal-code">Postal code <span class="optional-label">Optional</span></label><input id="postal-code" name="postalCode" type="text" maxlength="20" autocomplete="postal-code" value="<c:out value='${profileForm.postalCode}' />"></div>
                        </div>
                    </section>
                    <section class="profile-form-section" aria-labelledby="academic-information-title">
                        <div class="profile-form-heading"><span class="profile-section-icon profile-icon-academic" aria-hidden="true">03</span><div><h2 id="academic-information-title">Academic information</h2><p>Required fields help identify your current program and study stage.</p></div></div>
                        <div class="form-grid">
                            <div class="form-field"><label for="institution-name">College / institution <span class="required-label">Required</span></label><input id="institution-name" name="institutionName" type="text" maxlength="160" autocomplete="organization" value="<c:out value='${profileForm.institutionName}' />" required></div>
                            <div class="form-field"><label for="course-name">Course / program <span class="required-label">Required</span></label><input id="course-name" name="courseName" type="text" maxlength="100" value="<c:out value='${profileForm.courseName}' />" required></div>
                            <div class="form-field"><label for="department-name">Department <span class="optional-label">Optional</span></label><input id="department-name" name="departmentName" type="text" maxlength="100" value="<c:out value='${profileForm.departmentName}' />"></div>
                            <div class="form-field"><label for="semester-number">Current semester <span class="required-label">Required</span></label><input id="semester-number" name="semesterNumber" type="number" min="1" max="12" step="1" value="<c:out value='${profileForm.semesterNumber}' />" required></div>
                            <div class="form-field"><label for="study-year">Year of study <span class="optional-label">Optional</span></label><input id="study-year" name="studyYear" type="number" min="1" max="10" step="1" value="<c:out value='${profileForm.studyYear}' />"></div>
                            <div class="form-field"><label for="graduation-year">Expected graduation year <span class="optional-label">Optional</span></label><input id="graduation-year" name="graduationYear" type="number" min="1950" max="2200" step="1" value="<c:out value='${profileForm.graduationYear}' />"></div>
                        </div>
                    </section>
                    <div class="profile-form-actions"><p>Student ID, phone, address, and graduation year are optional. Passwords and access roles are managed separately.</p><button class="button button-primary button-large" type="submit">Save profile</button></div>
                </form>
            </c:otherwise>
        </c:choose>
        <footer class="page-footer"><p>Profile completion requires your name, email, institution, course, and current semester.</p><p>MessAudit · Procurement &amp; Operations Management</p></footer>
    </main>
</body>
</html>
