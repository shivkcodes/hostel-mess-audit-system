<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="MessAudit brings hostel mess procurement, receipt verification, stock visibility and measured food tracking together.">
    <title>MessAudit | Procurement &amp; Operations Management</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/dashboard.css?v=20261029-coral-navy">
    <script src="${pageContext.request.contextPath}/js/auth.js?v=20261028-public-m17" defer></script>
</head>
<body class="public-body">
    <header class="public-header">
        <a class="brand public-brand" href="${pageContext.request.contextPath}/#home" aria-label="MessAudit home">
            <span class="brand-mark" aria-hidden="true">M</span>
            <span class="brand-copy"><span class="brand-title">MessAudit</span><span class="brand-subtitle">Procurement &amp; Operations Management</span></span>
        </a>
        <details class="public-menu" open>
            <summary class="public-menu-toggle" aria-label="Toggle site navigation"><span></span><span></span><span></span></summary>
            <nav class="public-nav" aria-label="Public navigation">
                <a href="${pageContext.request.contextPath}/#home" aria-current="page">Home</a>
                <a href="${pageContext.request.contextPath}/#features">Features</a>
                <a href="${pageContext.request.contextPath}/#how-it-works">How it works</a>
                <a href="${pageContext.request.contextPath}/#about">About</a>
                <a class="public-nav-signin" href="${pageContext.request.contextPath}/login">Sign in</a>
                <a class="button button-primary public-nav-register" href="${pageContext.request.contextPath}/register">Register</a>
            </nav>
        </details>
    </header>

    <main>
        <section id="home" class="public-hero">
            <div class="public-hero-content">
                <p class="eyebrow"><span class="hero-eyebrow-dot" aria-hidden="true"></span> Clear records for daily mess operations</p>
                <h1>Smarter Mess Operations.<br><span>Transparent Procurement.</span><br>Accountable Inventory.</h1>
                <p class="public-hero-copy">Manage purchase orders, verify delivered quantities, monitor accepted stock, and record meals and measured leftovers in one traceable workspace.</p>
                <div class="public-hero-actions">
                    <a class="button button-primary button-large" href="#features">Explore features <span aria-hidden="true">→</span></a>
                    <a class="button button-secondary button-large" href="${pageContext.request.contextPath}/login">Sign in to MessAudit</a>
                </div>
                <p class="public-hero-note"><span aria-hidden="true">✓</span> Operational information stays tied to recorded evidence.</p>
            </div>
            <div class="hero-visual" aria-label="Illustration of procurement records, stock visibility, and meal tracking">
                <div class="hero-orbit hero-orbit-one"></div><div class="hero-orbit hero-orbit-two"></div>
                <div class="hero-summary-card">
                    <div class="hero-summary-top"><span class="hero-summary-icon" aria-hidden="true">✦</span><span class="hero-summary-label">Operations overview</span><span class="hero-live-dot" aria-hidden="true"></span></div>
                    <div class="hero-summary-title">A clearer view of the day</div>
                    <div class="hero-summary-caption">Procurement · inventory · meals</div>
                    <div class="hero-mini-metrics">
                        <div><span class="hero-mini-icon mini-lilac" aria-hidden="true">↗</span><span class="hero-mini-copy"><strong>Orders</strong><small>Recorded and traceable</small></span></div>
                        <div><span class="hero-mini-icon mini-sage" aria-hidden="true">▤</span><span class="hero-mini-copy"><strong>Stock</strong><small>Accepted goods tracked</small></span></div>
                        <div><span class="hero-mini-icon mini-amber" aria-hidden="true">◷</span><span class="hero-mini-copy"><strong>Meals</strong><small>Measurements kept distinct</small></span></div>
                    </div>
                    <p class="hero-summary-foot"><span aria-hidden="true">●</span> One workspace for operational records</p>
                </div>
                <span class="hero-float hero-float-top" aria-hidden="true">Procurement <b>✓</b></span>
                <span class="hero-float hero-float-bottom" aria-hidden="true"><b>↗</b> Stock visibility</span>
            </div>
            <a class="hero-scroll-cue" href="#features"><span aria-hidden="true">↓</span> Discover the workflow</a>
        </section>

        <section id="features" class="public-section feature-section">
            <div class="public-section-heading">
                <p class="eyebrow">One connected workflow</p>
                <h2>Operations, made easier to follow.</h2>
                <p>Keep procurement, stock, meal measurements, and vendor delivery evidence together without mixing what each record means.</p>
            </div>
            <div class="feature-grid">
                <article class="feature-card"><span class="feature-icon feature-lilac" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M4 5.5h2l2.1 10.1a1.7 1.7 0 0 0 1.7 1.4h7.6a1.7 1.7 0 0 0 1.7-1.3L21 9H7"/><circle cx="10" cy="20" r="1.3"/><circle cx="17.5" cy="20" r="1.3"/></svg></span><h3>Procurement &amp; purchase orders</h3><p>Record approved orders, their ingredient lines, quantities, and vendor relationships.</p><a href="${pageContext.request.contextPath}/login">Explore workspace <span aria-hidden="true">→</span></a></article>
                <article class="feature-card"><span class="feature-icon feature-sage" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m12 3.5 8 4.2v8.6l-8 4.2-8-4.2V7.7z"/><path d="m4.2 7.8 7.8 4.1 7.8-4.1M12 12v8.3M8 5.6l8 4.2"/></svg></span><h3>Goods receipt verification</h3><p>Compare received, accepted, and rejected quantities against the original order.</p><a href="${pageContext.request.contextPath}/login">Explore workspace <span aria-hidden="true">→</span></a></article>
                <article class="feature-card"><span class="feature-icon feature-blue" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m4 7 8-4 8 4-8 4zM4 7v10l8 4 8-4V7M12 11v10"/></svg></span><h3>Inventory &amp; stock monitoring</h3><p>Accepted goods post to stock records; current balances and reorder levels stay visible.</p><a href="${pageContext.request.contextPath}/login">Explore workspace <span aria-hidden="true">→</span></a></article>
                <article class="feature-card"><span class="feature-icon feature-amber" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3.4 2M8 3.8l-1.4-1M16 3.8l1.4-1"/></svg></span><h3>Preparation &amp; measured leftovers</h3><p>Keep meal counts, prepared amounts, and measured leftovers with their methods and dates.</p><a href="${pageContext.request.contextPath}/login">Explore workspace <span aria-hidden="true">→</span></a></article>
                <article class="feature-card"><span class="feature-icon feature-rose" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M8 4.5h8a2 2 0 0 1 2 2v13H6v-13a2 2 0 0 1 2-2Z"/><path d="M9 4.5v-1h6v1M9 11l2 2 4-4M9 17h6"/></svg></span><h3>Vendor audit evidence</h3><p>Review order quantities alongside verified receipt evidence, unit by unit.</p><a href="${pageContext.request.contextPath}/login">Explore workspace <span aria-hidden="true">→</span></a></article>
                <article class="feature-card"><span class="feature-icon feature-plum" aria-hidden="true"><svg viewBox="0 0 24 24"><rect x="3.5" y="3.5" width="7" height="7" rx="1.4"/><rect x="13.5" y="3.5" width="7" height="5" rx="1.4"/><rect x="13.5" y="10.5" width="7" height="10" rx="1.4"/><rect x="3.5" y="12.5" width="7" height="8" rx="1.4"/></svg></span><h3>Dashboard &amp; analytics</h3><p>See live database summaries and analytics generated from the application's stored records.</p><a href="${pageContext.request.contextPath}/login">Explore workspace <span aria-hidden="true">→</span></a></article>
            </div>
        </section>

        <section id="how-it-works" class="public-section workflow-section">
            <div class="workflow-copy"><p class="eyebrow">How it works</p><h2>From order to review, with the evidence in between.</h2><p>Each stage builds on recorded operational information, helping teams follow what was requested, what arrived, and what was measured.</p><a class="text-link" href="${pageContext.request.contextPath}/register">Create a read-only account <span aria-hidden="true">→</span></a></div>
            <ol class="workflow-list">
                <li><span>01</span><div><h3>Create procurement orders</h3><p>Record a vendor, mess block, order date, and ingredient quantities.</p></div></li>
                <li><span>02</span><div><h3>Verify delivered quantities</h3><p>Enter received, accepted, and rejected quantities against each order item.</p></div></li>
                <li><span>03</span><div><h3>Update stock from accepted goods</h3><p>Only accepted quantities post to inventory and its transaction history.</p></div></li>
                <li><span>04</span><div><h3>Review balances and reorder levels</h3><p>Check current stock by mess block and ingredient.</p></div></li>
                <li><span>05</span><div><h3>Record meals and measurements</h3><p>Store meals served, prepared quantity, and measured leftovers when available.</p></div></li>
                <li><span>06</span><div><h3>Compare vendor delivery evidence</h3><p>Review order quantities against verified receipts, without inferring fault from food leftovers.</p></div></li>
            </ol>
        </section>

        <section class="public-principles" aria-label="Operating principles">
            <div><span aria-hidden="true">⌁</span><h3>Traceable records</h3><p>Keep quantities connected to their source orders, receipts, blocks, and dates.</p></div>
            <div><span aria-hidden="true">◈</span><h3>Role-aware access</h3><p>ADMIN and READ_ONLY permissions are checked on protected server routes.</p></div>
            <div><span aria-hidden="true">▦</span><h3>Stock visibility</h3><p>Accepted receipt quantities update the inventory balance and ledger together.</p></div>
            <div><span aria-hidden="true">◎</span><h3>Responsible reporting</h3><p>Measured leftovers are recorded as leftovers; they do not by themselves prove disposal or vendor fault.</p></div>
        </section>

        <section id="about" class="public-section about-section">
            <div class="about-mark" aria-hidden="true"><span>MA</span><i></i><i></i><i></i></div>
            <div><p class="eyebrow">About MessAudit</p><h2>Better operational decisions begin with clearer records.</h2><p>MessAudit brings procurement, goods receipt, inventory, meal measurements, and vendor delivery evidence into one institutional operations workspace. It supports traceability, stock visibility, and quantity reconciliation so responsible users can review the records behind their decisions.</p><p>The application reports what its data supports. A measured leftover is not automatically discarded food, and food wastage alone is never used to assign vendor fault.</p></div>
        </section>

        <section class="public-final-cta"><div><p class="eyebrow">Mess operations, in view</p><h2>Bring daily records into one clear workspace.</h2><p>Register for read-only access or sign in with an administrator-provisioned account.</p></div><div class="public-hero-actions"><a class="button button-primary button-large" href="${pageContext.request.contextPath}/register">Create an account</a><a class="button button-secondary button-large" href="${pageContext.request.contextPath}/login">Sign in</a></div></section>
    </main>

    <footer class="public-footer">
        <div class="footer-brand"><a class="brand" href="${pageContext.request.contextPath}/#home"><span class="brand-mark" aria-hidden="true">M</span><span class="brand-copy"><span class="brand-title">MessAudit</span><span class="brand-subtitle">Procurement &amp; Operations Management</span></span></a><p>Traceable procurement and operational records for institutional mess teams.</p></div>
        <div class="footer-links"><div><h2>Explore</h2><a href="${pageContext.request.contextPath}/#features">Features</a><a href="${pageContext.request.contextPath}/#how-it-works">How it works</a><a href="${pageContext.request.contextPath}/#about">About</a></div><div><h2>Workspace</h2><a href="${pageContext.request.contextPath}/login">Sign in</a><a href="${pageContext.request.contextPath}/register">Register</a></div></div>
        <div class="footer-bottom"><span>© <c:out value="${currentYear}" /> MessAudit</span><a href="${pageContext.request.contextPath}/#home">Back to top ↑</a></div>
    </footer>
</body>
</html>
