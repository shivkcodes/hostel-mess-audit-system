-- Read-only checks to run after 01_create_tables.sql, 02_add_constraints.sql,
-- and 03_seed_synthetic_data.sql have completed successfully.

SELECT 1 AS connection_check FROM dual;

SELECT table_name
FROM user_tables
WHERE table_name IN (
    'VENDORS', 'MESS_BLOCKS', 'INGREDIENTS', 'PURCHASE_ORDERS',
    'PURCHASE_ORDER_ITEMS', 'DELIVERY_RECEIPTS', 'DELIVERY_RECEIPT_ITEMS',
    'INVENTORY_BALANCES', 'INVENTORY_TRANSACTIONS', 'DAILY_MESS_RECORDS',
    'VENDOR_CONTRACT_RULES', 'VENDOR_PENALTY_REVIEWS', 'AUDIT_LOG'
)
ORDER BY table_name;

SELECT table_name, constraint_name, constraint_type, status
FROM user_constraints
WHERE table_name IN (
    'VENDORS', 'MESS_BLOCKS', 'INGREDIENTS', 'PURCHASE_ORDERS',
    'PURCHASE_ORDER_ITEMS', 'DELIVERY_RECEIPTS', 'DELIVERY_RECEIPT_ITEMS',
    'INVENTORY_BALANCES', 'INVENTORY_TRANSACTIONS', 'DAILY_MESS_RECORDS',
    'VENDOR_CONTRACT_RULES', 'VENDOR_PENALTY_REVIEWS', 'AUDIT_LOG'
)
ORDER BY table_name, constraint_type, constraint_name;

SELECT 'VENDORS' entity_name, COUNT(*) row_count FROM vendors
WHERE vendor_code LIKE 'DEMO-%'
UNION ALL
SELECT 'MESS_BLOCKS', COUNT(*) FROM mess_blocks
WHERE block_code LIKE 'DEMO-%'
UNION ALL
SELECT 'INGREDIENTS', COUNT(*) FROM ingredients
WHERE ingredient_code LIKE 'DEMO-%'
UNION ALL
SELECT 'PURCHASE_ORDERS', COUNT(*) FROM purchase_orders
WHERE po_number LIKE 'DEMO-%'
UNION ALL
SELECT 'CONTRACT_RULES', COUNT(*) FROM vendor_contract_rules
WHERE rule_code LIKE 'DEMO-%'
UNION ALL
SELECT 'DAILY_MESS_RECORDS', COUNT(*) FROM daily_mess_records d
JOIN mess_blocks b ON b.block_id = d.block_id
WHERE b.block_code LIKE 'DEMO-%'
ORDER BY entity_name;

-- Compare quantities by ingredient; quantities with different units are not added together.
SELECT p.po_number,
       i.ingredient_code,
       i.base_unit,
       oi.ordered_qty,
       NVL(SUM(dri.received_qty), 0) total_received_qty,
       NVL(SUM(dri.accepted_qty), 0) total_accepted_qty,
       NVL(SUM(dri.rejected_qty), 0) total_rejected_qty,
       GREATEST(oi.ordered_qty - NVL(SUM(dri.received_qty), 0), 0) short_qty,
       GREATEST(NVL(SUM(dri.received_qty), 0) - oi.ordered_qty, 0) excess_qty
FROM purchase_orders p
JOIN purchase_order_items oi ON oi.po_id = p.po_id
JOIN ingredients i ON i.ingredient_id = oi.ingredient_id
LEFT JOIN delivery_receipt_items dri
       ON dri.po_id = oi.po_id AND dri.po_item_id = oi.po_item_id
WHERE p.po_number LIKE 'DEMO-%'
GROUP BY p.po_number, i.ingredient_code, i.base_unit, oi.ordered_qty
ORDER BY p.po_number, i.ingredient_code;

-- Synthetic invoice assumption: billed amount equals accepted quantity x agreed PO price.
SELECT r.invoice_reference,
       r.invoice_amount,
       ROUND(NVL(SUM(dri.accepted_qty * oi.unit_price), 0), 2) expected_accepted_amount,
       CASE
           WHEN ABS(r.invoice_amount - ROUND(NVL(SUM(dri.accepted_qty * oi.unit_price), 0), 2)) <= 0.01
           THEN 'MATCH'
           ELSE 'REVIEW'
       END invoice_check
FROM delivery_receipts r
JOIN purchase_orders p ON p.po_id = r.po_id
LEFT JOIN delivery_receipt_items dri ON dri.receipt_id = r.receipt_id
LEFT JOIN purchase_order_items oi
       ON oi.po_id = dri.po_id AND oi.po_item_id = dri.po_item_id
WHERE p.po_number LIKE 'DEMO-%'
GROUP BY r.invoice_reference, r.invoice_amount
ORDER BY r.invoice_reference;

-- Monthly per-capita leftovers are shown only when every daily measurement is present
-- and the month has at least one meal served.
SELECT b.block_code,
       TRUNC(d.record_date, 'MM') month_start,
       COUNT(*) record_count,
       COUNT(d.leftover_kg) measured_record_count,
       COUNT(*) - COUNT(d.leftover_kg) unmeasured_record_count,
       SUM(d.meals_served) meals_served,
       SUM(d.leftover_kg) total_measured_leftover_kg,
       CASE
           WHEN COUNT(*) = COUNT(d.leftover_kg) AND SUM(d.meals_served) > 0
           THEN ROUND(SUM(d.leftover_kg) / SUM(d.meals_served), 3)
           ELSE NULL
       END per_capita_leftover_kg
FROM daily_mess_records d
JOIN mess_blocks b ON b.block_id = d.block_id
WHERE b.block_code LIKE 'DEMO-%'
GROUP BY b.block_code, TRUNC(d.record_date, 'MM')
ORDER BY b.block_code, month_start;

-- Cursor-report source query: rows below their configured reorder level.
SELECT b.block_code, i.ingredient_code, i.base_unit,
       ib.on_hand_qty, ib.reorder_level
FROM inventory_balances ib
JOIN mess_blocks b ON b.block_id = ib.block_id
JOIN ingredients i ON i.ingredient_id = ib.ingredient_id
WHERE b.block_code LIKE 'DEMO-%'
  AND ib.on_hand_qty < ib.reorder_level
ORDER BY b.block_code, i.ingredient_code;

SELECT rule_code, discrepancy_type, tolerance_percent, penalty_percent,
       max_penalty, terms_text
FROM vendor_contract_rules
WHERE rule_code LIKE 'DEMO-%'
ORDER BY rule_code;

-- No proposal is seeded: penalty proposals will be created by the later PL/SQL phase.
SELECT COUNT(*) demo_penalty_review_count
FROM vendor_penalty_reviews r
JOIN purchase_orders p ON p.po_id = r.po_id
WHERE p.po_number LIKE 'DEMO-%';
