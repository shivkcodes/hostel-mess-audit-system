(() => {
    "use strict";

    const input = document.querySelector("[data-admin-search-input]");
    const scope = document.querySelector("[data-admin-search-scope]");
    const status = document.querySelector("[data-admin-search-status]");
    if (!input || !scope) return;

    const rows = Array.from(scope.querySelectorAll("tbody tr"))
        .filter((row) => !row.querySelector(".empty-cell"));
    const entries = Array.from(scope.querySelectorAll("[data-admin-search-row]"));

    const apply = () => {
        const query = input.value.trim().toLocaleLowerCase();
        let total = 0;
        let visible = 0;
        [...rows, ...entries].forEach((item) => {
            total += 1;
            const matches = !query || item.textContent.toLocaleLowerCase().includes(query);
            item.hidden = !matches;
            if (matches) visible += 1;
        });
        if (status) {
            if (!query) status.textContent = "";
            else if (visible === 0) status.textContent = "No records in this category match the search.";
            else status.textContent = `${visible} of ${total} matching category records shown`;
        }
    };

    input.addEventListener("input", apply);
    apply();
})();
