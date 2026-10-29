(() => {
    "use strict";

    const SVG_NS = "http://www.w3.org/2000/svg";
    const COLORS = {
        accent: "#8B6CE8",
        blue: "#6686C6",
        green: "#4D8A70",
        amber: "#D49A32",
        red: "#B95D69",
        reorder: "#B8ACC9",
        grid: "#E9E3F2",
        axis: "#625B72",
        low: "#75500F"
    };
    const SERIES = {
        ordered: { label: "Ordered", color: COLORS.accent },
        received: { label: "Verified received", color: COLORS.blue },
        accepted: { label: "Accepted", color: COLORS.green },
        rejected: { label: "Rejected", color: COLORS.red },
        prepared: { label: "Prepared", color: COLORS.accent },
        leftovers: { label: "Measured leftovers", color: COLORS.blue },
        onHand: { label: "On hand", color: COLORS.accent },
        reorder: { label: "Reorder level", color: COLORS.reorder }
    };

    function node(name, attributes = {}) {
        const element = document.createElementNS(SVG_NS, name);
        Object.entries(attributes).forEach(([key, value]) => element.setAttribute(key, String(value)));
        return element;
    }

    function svgCanvas(width, height, description) {
        const svg = node("svg", {
            viewBox: `0 0 ${width} ${height}`,
            width,
            height,
            role: "img",
            "aria-label": description,
            focusable: "false"
        });
        return svg;
    }

    function addText(parent, value, x, y, className, anchor = "start") {
        const text = node("text", { x, y, "text-anchor": anchor, class: className });
        text.textContent = value;
        parent.appendChild(text);
        return text;
    }

    function addTitle(parent, value) {
        const title = node("title");
        title.textContent = value;
        parent.appendChild(title);
    }

    function addLine(parent, x1, y1, x2, y2, className) {
        parent.appendChild(node("line", { x1, y1, x2, y2, class: className }));
    }

    function quantity(value) {
        if (value === null || value === undefined || !Number.isFinite(Number(value))) return "Not measured";
        return Number(value).toLocaleString(undefined, { maximumFractionDigits: 3 });
    }

    function dateLabel(value, style = "short") {
        if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return "Date unavailable";
        const [year, month, day] = value.split("-").map(Number);
        const date = new Date(year, month - 1, day);
        if (style === "month") {
            return date.toLocaleDateString(undefined, { month: "short", year: "2-digit" });
        }
        return date.toLocaleDateString(undefined, { day: "numeric", month: "short" });
    }

    function setStatus(root, message, isError = false) {
        const status = root.querySelector("[data-chart-status]");
        if (status) {
            status.textContent = message;
            status.hidden = false;
            status.classList.toggle("analytics-chart-status-error", isError);
        }
        const viewport = root.querySelector("[data-chart-viewport]");
        if (viewport) viewport.replaceChildren();
    }

    function hideStatus(root) {
        const status = root.querySelector("[data-chart-status]");
        if (status) status.hidden = true;
    }

    function parsePayload(root) {
        const parsed = JSON.parse(root.dataset.chartData || "[]");
        if (!Array.isArray(parsed)) throw new TypeError("Chart payload must be an array.");
        return parsed;
    }

    function unitsFor(rows) {
        return [...new Set(rows.map(row => String(row.baseUnit || "").trim()).filter(Boolean))]
            .sort((left, right) => left.localeCompare(right, undefined, { sensitivity: "base" }));
    }

    function configureUnitSelect(root, units, onChange) {
        const select = root.querySelector("[data-chart-unit]");
        if (!select) return null;
        select.replaceChildren();
        units.forEach(unit => {
            const option = document.createElement("option");
            option.value = unit;
            option.textContent = unit;
            select.appendChild(option);
        });
        if (units.length === 0) {
            select.disabled = true;
            return null;
        }
        select.value = units[0];
        select.disabled = units.length < 2;
        if (units.length > 1) select.addEventListener("change", onChange);
        return select;
    }

    function renderLegend(root, series, hidden, onToggle) {
        const legend = root.querySelector("[data-chart-legend]");
        if (!legend) return;
        legend.replaceChildren();
        series.forEach(item => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "analytics-legend-button";
            button.dataset.series = item.key;
            button.setAttribute("aria-pressed", String(!hidden.has(item.key)));
            const swatch = document.createElement("span");
            swatch.className = "analytics-legend-swatch";
            swatch.style.setProperty("--series-color", item.color);
            swatch.setAttribute("aria-hidden", "true");
            button.append(swatch, document.createTextNode(item.label));
            button.addEventListener("click", () => onToggle(item.key));
            legend.appendChild(button);
        });
    }

    function drawNumericAxis(svg, left, top, plotWidth, plotHeight, maxValue, unit = "") {
        const axisMax = maxValue > 0 ? maxValue : 1;
        for (let tick = 0; tick <= 4; tick++) {
            const value = axisMax * tick / 4;
            const y = top + plotHeight - plotHeight * tick / 4;
            addLine(svg, left, y, left + plotWidth, y, "chart-gridline");
            addText(svg, `${quantity(value)}${unit ? ` ${unit}` : ""}`, left - 10, y + 4, "chart-axis-label", "end");
        }
        return axisMax;
    }

    function renderProcurement(root, rows, unit, hidden) {
        const selected = rows.filter(row => row.baseUnit === unit);
        const visible = selected.filter(() => !hidden.has("ordered"));
        renderLegend(root, [{ key: "ordered", ...SERIES.ordered }], hidden, key => {
            hidden.has(key) ? hidden.delete(key) : hidden.add(key);
            renderProcurement(root, rows, unit, hidden);
        });
        if (selected.length === 0) return false;

        const left = 86;
        const right = 28;
        const top = 24;
        const bottom = 68;
        const step = 72;
        const width = Math.max(760, left + right + selected.length * step);
        const height = 340;
        const plotWidth = width - left - right;
        const plotHeight = height - top - bottom;
        const maxValue = Math.max(0, ...selected.map(row => Number(row.orderedQty) || 0));
        const axisMax = drawNumericAxis(root.querySelector("[data-chart-viewport]").appendChild(
            svgCanvas(width, height, `Monthly ordered quantities in ${unit}`)), left, top, plotWidth, plotHeight, maxValue);
        const svg = root.querySelector("[data-chart-viewport]").lastElementChild;
        if (!svg) return false;
        addText(svg, `Ordered quantity (${unit})`, left, 16, "chart-axis-title");
        selected.forEach((row, index) => {
            const value = Number(row.orderedQty) || 0;
            const barWidth = Math.min(42, step * 0.62);
            const x = left + (index + 0.5) * plotWidth / selected.length - barWidth / 2;
            const barHeight = value / axisMax * plotHeight;
            const y = top + plotHeight - barHeight;
            if (!hidden.has("ordered")) {
                const bar = node("rect", {
                    x, y, width: barWidth, height: Math.max(0, barHeight), rx: 7,
                    class: "chart-bar chart-bar-ordered", tabindex: 0,
                    "aria-label": `${dateLabel(row.activityMonth, "month")}: ${quantity(value)} ${unit} ordered`
                });
                addTitle(bar, `${dateLabel(row.activityMonth, "month")} · ${quantity(value)} ${unit} ordered`);
                svg.appendChild(bar);
            }
            addText(svg, dateLabel(row.activityMonth, "month"), x + barWidth / 2, top + plotHeight + 24,
                "chart-axis-label", "middle");
        });
        if (visible.length === 0) {
            addText(svg, "Use the legend to show ordered quantities", width / 2, height / 2, "chart-empty-note", "middle");
        }
        root.querySelector("[data-chart-viewport]").replaceChildren(svg);
        return true;
    }

    function truncateLabel(value, maxLength = 30) {
        const text = String(value || "");
        return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text;
    }

    function renderInventory(root, rows, unit, hidden) {
        const selected = rows.filter(row => row.baseUnit === unit);
        renderLegend(root, [
            { key: "onHand", ...SERIES.onHand },
            { key: "reorder", ...SERIES.reorder }
        ], hidden, key => {
            hidden.has(key) ? hidden.delete(key) : hidden.add(key);
            renderInventory(root, rows, unit, hidden);
        });
        if (selected.length === 0) return false;

        const left = 300;
        const right = 54;
        const top = 44;
        const rowHeight = 48;
        const height = top + selected.length * rowHeight + 28;
        const width = 1040;
        const plotWidth = width - left - right;
        const maxValue = Math.max(0, ...selected.flatMap(row => [Number(row.onHandQty) || 0, Number(row.reorderLevel) || 0]));
        const svg = svgCanvas(width, height, `On-hand stock compared with reorder level in ${unit}`);
        const axisMax = maxValue > 0 ? maxValue : 1;
        for (let tick = 0; tick <= 4; tick++) {
            const x = left + plotWidth * tick / 4;
            addLine(svg, x, top - 17, x, height - 20, "chart-gridline");
            addText(svg, `${quantity(axisMax * tick / 4)} ${unit}`, x, top - 25, "chart-axis-label", "middle");
        }
        selected.forEach((row, index) => {
            const y = top + index * rowHeight;
            const low = Boolean(row.lowStock);
            const fullLabel = `${row.ingredientName} — ${row.blockName}`;
            const label = truncateLabel(fullLabel, 38);
            const labelElement = addText(svg, label, 8, y + 18, `chart-row-label${low ? " chart-row-label-low" : ""}`);
            addTitle(labelElement, fullLabel);
            if (low) addText(svg, "LOW", 8, y + 36, "chart-low-label");
            const values = [
                { key: "onHand", value: Number(row.onHandQty) || 0, dy: 0, color: low ? COLORS.amber : COLORS.accent, label: "On hand" },
                { key: "reorder", value: Number(row.reorderLevel) || 0, dy: 19, color: COLORS.reorder, label: "Reorder level" }
            ];
            values.forEach(series => {
                if (hidden.has(series.key)) return;
                const bar = node("rect", {
                    x: left, y: y + series.dy, width: Math.max(0, series.value / axisMax * plotWidth), height: 12, rx: 6,
                    fill: series.color, class: "chart-bar", tabindex: 0,
                    "aria-label": `${fullLabel}: ${series.label} ${quantity(series.value)} ${unit}${low ? ", low stock" : ""}`
                });
                addTitle(bar, `${fullLabel} · ${series.label}: ${quantity(series.value)} ${unit}${low ? " · below reorder level" : ""}`);
                svg.appendChild(bar);
            });
        });
        if (hidden.has("onHand") && hidden.has("reorder")) {
            addText(svg, "Use the legend to show a stock series", width / 2, height / 2, "chart-empty-note", "middle");
        }
        root.querySelector("[data-chart-viewport]").replaceChildren(svg);
        return true;
    }

    function linePathSegments(rows, valueKey, xFor, yFor) {
        const paths = [];
        let points = [];
        rows.forEach((row, index) => {
            const value = row[valueKey];
            if (value === null || value === undefined || !Number.isFinite(Number(value))) {
                if (points.length) paths.push(points);
                points = [];
                return;
            }
            points.push({ x: xFor(index), y: yFor(Number(value)), row, value: Number(value) });
        });
        if (points.length) paths.push(points);
        return paths;
    }

    function renderWastage(root, rows, hidden) {
        const availableValues = rows.flatMap(row => [row.preparedKg, row.measuredLeftoverKg])
            .filter(value => value !== null && value !== undefined && Number.isFinite(Number(value)))
            .map(Number);
        renderLegend(root, [
            { key: "prepared", ...SERIES.prepared },
            { key: "leftovers", ...SERIES.leftovers }
        ], hidden, key => {
            hidden.has(key) ? hidden.delete(key) : hidden.add(key);
            renderWastage(root, rows, hidden);
        });
        if (rows.length === 0 || availableValues.length === 0) return false;

        const left = 80;
        const right = 24;
        const top = 30;
        const bottom = 72;
        const step = 50;
        const width = Math.max(840, left + right + rows.length * step);
        const height = 360;
        const plotWidth = width - left - right;
        const plotHeight = height - top - bottom;
        const maxValue = Math.max(...availableValues);
        const svg = svgCanvas(width, height, "Daily measured food preparation and leftovers in kilograms");
        const axisMax = drawNumericAxis(svg, left, top, plotWidth, plotHeight, maxValue, "kg");
        const xFor = index => left + (rows.length <= 1 ? plotWidth / 2 : index * plotWidth / (rows.length - 1));
        const yFor = value => top + plotHeight - value / axisMax * plotHeight;
        const series = [
            { key: "prepared", field: "preparedKg", color: COLORS.accent, name: "Prepared", measurementCount: "preparedRecordCount" },
            { key: "leftovers", field: "measuredLeftoverKg", color: COLORS.blue, name: "Measured leftovers", measurementCount: "measuredRecordCount" }
        ];
        series.forEach(item => {
            if (hidden.has(item.key)) return;
            const segments = linePathSegments(rows, item.field, xFor, yFor);
            segments.forEach(points => {
                if (points.length > 1) {
                    const pathData = points.map((point, index) => `${index === 0 ? "M" : "L"}${point.x},${point.y}`).join(" ");
                    svg.appendChild(node("path", { d: pathData, class: "chart-line", stroke: item.color }));
                }
                points.forEach(point => {
                    const dailyPerMeal = point.row.weightedPerMealKg;
                    const perMealText = dailyPerMeal === null || dailyPerMeal === undefined
                        ? "Weighted leftover per meal: not available"
                        : `Weighted leftover per meal: ${quantity(dailyPerMeal)} kg/meal`;
                    const detail = `${dateLabel(point.row.reportDate)} · ${item.name}: ${quantity(point.value)} kg · ${quantity(point.row[item.measurementCount])} contributing measurements · ${perMealText}`;
                    const circle = node("circle", {
                        cx: point.x, cy: point.y, r: 5.5, fill: item.color, class: "chart-point", tabindex: 0,
                        "aria-label": detail
                    });
                    addTitle(circle, detail);
                    svg.appendChild(circle);
                });
            });
        });
        const labelInterval = Math.max(1, Math.ceil(rows.length / 12));
        rows.forEach((row, index) => {
            if (index % labelInterval !== 0 && index !== rows.length - 1) return;
            addText(svg, dateLabel(row.reportDate), xFor(index), top + plotHeight + 25, "chart-axis-label", "middle");
        });
        addText(svg, "Record date", left + plotWidth / 2, height - 12, "chart-axis-title", "middle");
        if (hidden.has("prepared") && hidden.has("leftovers")) {
            addText(svg, "Use the legend to show a measurement series", width / 2, height / 2, "chart-empty-note", "middle");
        }
        root.querySelector("[data-chart-viewport]").replaceChildren(svg);
        return true;
    }

    function renderVendor(root, rows, unit, hidden) {
        const selected = rows.filter(row => row.baseUnit === unit)
            .sort((left, right) => {
                const quantityDifference = (Number(right.orderedQty) || 0) - (Number(left.orderedQty) || 0);
                if (quantityDifference !== 0) return quantityDifference;
                const codeOrder = String(left.vendorCode || "").localeCompare(String(right.vendorCode || ""));
                return codeOrder || (Number(left.vendorId) || 0) - (Number(right.vendorId) || 0);
            })
            .slice(0, 10);
        const series = [
            { key: "ordered", ...SERIES.ordered },
            { key: "received", ...SERIES.received },
            { key: "accepted", ...SERIES.accepted },
            { key: "rejected", ...SERIES.rejected }
        ];
        renderLegend(root, series, hidden, key => {
            hidden.has(key) ? hidden.delete(key) : hidden.add(key);
            renderVendor(root, rows, unit, hidden);
        });
        if (selected.length === 0) return false;

        const left = 250;
        const right = 58;
        const top = 52;
        const rowHeight = 60;
        const width = 1080;
        const height = top + selected.length * rowHeight + 22;
        const plotWidth = width - left - right;
        const keys = ["orderedQty", "receivedQty", "acceptedQty", "rejectedQty"];
        const maxValue = Math.max(0, ...selected.flatMap(row => keys.map(key => Number(row[key]) || 0)));
        const axisMax = maxValue > 0 ? maxValue : 1;
        const svg = svgCanvas(width, height, `Vendor order and verified receipt quantities in ${unit}`);
        for (let tick = 0; tick <= 4; tick++) {
            const x = left + plotWidth * tick / 4;
            addLine(svg, x, top - 26, x, height - 12, "chart-gridline");
            addText(svg, `${quantity(axisMax * tick / 4)} ${unit}`, x, top - 34, "chart-axis-label", "middle");
        }
        selected.forEach((row, index) => {
            const y = top + index * rowHeight;
            const vendorLabel = `${row.vendorName || "Vendor"} (${row.vendorCode || ""})`;
            const label = addText(svg, truncateLabel(vendorLabel, 34), 8, y + 17, "chart-row-label");
            addTitle(label, vendorLabel);
            series.forEach((item, seriesIndex) => {
                if (hidden.has(item.key)) return;
                const field = keys[seriesIndex];
                const value = Number(row[field]) || 0;
                const bar = node("rect", {
                    x: left, y: y + 3 + seriesIndex * 12, width: Math.max(0, value / axisMax * plotWidth), height: 8, rx: 4,
                    fill: item.color, class: "chart-bar", tabindex: 0,
                    "aria-label": `${vendorLabel}: ${item.label} ${quantity(value)} ${unit}`
                });
                addTitle(bar, `${vendorLabel} · ${item.label}: ${quantity(value)} ${unit}`);
                svg.appendChild(bar);
            });
        });
        if (series.every(item => hidden.has(item.key))) {
            addText(svg, "Use the legend to show a quantity series", width / 2, height / 2, "chart-empty-note", "middle");
        }
        root.querySelector("[data-chart-viewport]").replaceChildren(svg);
        return true;
    }

    function initialize(root) {
        if (root.dataset.chartAvailable !== "true") {
            setStatus(root, "Analytics data is unavailable. The existing data tables show the page status.", true);
            return;
        }
        let rows;
        try {
            rows = parsePayload(root);
        } catch (error) {
            setStatus(root, "Chart data could not be read. The table remains available.", true);
            return;
        }
        if (rows.length === 0) {
            setStatus(root, "No matching records are available for this chart.");
            return;
        }

        const kind = root.dataset.analyticsChart;
        const hidden = new Set();
        let render = () => false;
        if (kind === "procurement") {
            const units = unitsFor(rows);
            const select = configureUnitSelect(root, units, () => render());
            if (!select) {
                setStatus(root, "No ordered quantities are available for the selected dates.");
                return;
            }
            render = () => {
                const success = renderProcurement(root, rows, select.value, hidden);
                success ? hideStatus(root) : setStatus(root, `No monthly quantity rows are available for ${select.value}.`);
                return success;
            };
            render();
        } else if (kind === "inventory") {
            const units = unitsFor(rows);
            const select = configureUnitSelect(root, units, () => render());
            if (!select) {
                setStatus(root, "No inventory balances match the current filters.");
                return;
            }
            render = () => {
                const success = renderInventory(root, rows, select.value, hidden);
                success ? hideStatus(root) : setStatus(root, `No inventory balances are available in ${select.value}.`);
                return success;
            };
            render();
        } else if (kind === "wastage") {
            render = () => {
                const success = renderWastage(root, rows, hidden);
                success ? hideStatus(root) : setStatus(root, "No measured preparation or leftover quantities are available for the matching dates.");
                return success;
            };
            render();
        } else if (kind === "vendor") {
            const units = unitsFor(rows);
            const select = configureUnitSelect(root, units, () => render());
            if (!select) {
                setStatus(root, "No non-cancelled order quantities match these filters.");
                return;
            }
            render = () => {
                const success = renderVendor(root, rows, select.value, hidden);
                success ? hideStatus(root) : setStatus(root, `No vendor quantities are available in ${select.value}.`);
                return success;
            };
            render();
        }
    }

    document.querySelectorAll("[data-analytics-chart]").forEach(root => {
        try {
            initialize(root);
        } catch (error) {
            setStatus(root, "This chart could not be rendered. The underlying table remains available.", true);
        }
    });
})();
