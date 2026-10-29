(() => {
    "use strict";

    const link = document.querySelector("[data-status-url]");
    if (!link) return;

    const label = link.querySelector("[data-db-status-label]");
    const dot = link.querySelector("[data-db-status-dot]");
    const cacheKey = "messaudit.database-status.v1";
    const cacheDurationMs = 60_000;

    const apply = (status, checkedAt) => {
        const connected = status === "CONNECTED";
        label.textContent = connected ? "Connected" : "Connection issue";
        link.setAttribute("aria-label", connected
            ? "Database status: Connected. Open connection diagnostics."
            : "Database status: Connection issue. Open connection diagnostics.");
        link.classList.toggle("db-status-connected", connected);
        link.classList.toggle("db-status-unavailable", !connected);
        dot.classList.toggle("db-status-dot-connected", connected);
        dot.classList.toggle("db-status-dot-unavailable", !connected);
        if (checkedAt) link.dataset.checkedAt = checkedAt;
    };

    const save = (status, checkedAt) => {
        try {
            sessionStorage.setItem(cacheKey, JSON.stringify({ status, checkedAt, cachedAt: Date.now() }));
        } catch (_) {
            // The live indicator still works when browser storage is disabled.
        }
    };

    const pageCheck = document.querySelector("[data-health-status]");
    if (pageCheck) {
        const status = pageCheck.dataset.healthStatus;
        const checkedAt = pageCheck.dataset.healthCheckedAt;
        apply(status, checkedAt);
        save(status, checkedAt);
        return;
    }

    let cached = null;
    try {
        cached = JSON.parse(sessionStorage.getItem(cacheKey) || "null");
    } catch (_) {
        cached = null;
    }
    if (cached && (cached.status === "CONNECTED" || cached.status === "UNAVAILABLE")
            && Number.isFinite(cached.cachedAt) && Date.now() - cached.cachedAt < cacheDurationMs) {
        apply(cached.status, cached.checkedAt);
        return;
    }

    fetch(link.dataset.statusUrl, {
        method: "GET",
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" }
    }).then(async (response) => {
        const result = await response.json();
        const status = result.status === "CONNECTED" && response.ok ? "CONNECTED" : "UNAVAILABLE";
        const checkedAt = typeof result.checkedAt === "string" ? result.checkedAt : "";
        apply(status, checkedAt);
        save(status, checkedAt);
    }).catch(() => {
        apply("UNAVAILABLE", "");
    });
})();
