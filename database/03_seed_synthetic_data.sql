-- Synthetic demonstration rows only; no real student or vendor data.
-- All MERGE statements insert missing natural keys and leave existing rows untouched.
-- The final COMMIT persists the seed data. Do not run this file before the schema scripts.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

MERGE INTO vendors target
USING (SELECT 'DEMO-V001' vendor_code, 'Campus Grain Demo' vendor_name,
              'Demo Contact A' contact_name, '000-000-0101' phone,
              'vendor-a@example.invalid' email FROM dual) source
ON (target.vendor_code = source.vendor_code)
WHEN NOT MATCHED THEN INSERT (vendor_code, vendor_name, contact_name, phone, email)
VALUES (source.vendor_code, source.vendor_name, source.contact_name, source.phone, source.email);

MERGE INTO vendors target
USING (SELECT 'DEMO-V002' vendor_code, 'Fresh Dairy Demo' vendor_name,
              'Demo Contact B' contact_name, '000-000-0102' phone,
              'vendor-b@example.invalid' email FROM dual) source
ON (target.vendor_code = source.vendor_code)
WHEN NOT MATCHED THEN INSERT (vendor_code, vendor_name, contact_name, phone, email)
VALUES (source.vendor_code, source.vendor_name, source.contact_name, source.phone, source.email);

MERGE INTO mess_blocks target
USING (SELECT 'DEMO-BLOCK-A' block_code, 'North Hostel Mess' block_name FROM dual) source
ON (target.block_code = source.block_code)
WHEN NOT MATCHED THEN INSERT (block_code, block_name) VALUES (source.block_code, source.block_name);

MERGE INTO mess_blocks target
USING (SELECT 'DEMO-BLOCK-B' block_code, 'South Hostel Mess' block_name FROM dual) source
ON (target.block_code = source.block_code)
WHEN NOT MATCHED THEN INSERT (block_code, block_name) VALUES (source.block_code, source.block_name);

MERGE INTO ingredients target
USING (SELECT 'DEMO-RICE' ingredient_code, 'Rice' ingredient_name, 'KG' base_unit FROM dual) source
ON (target.ingredient_code = source.ingredient_code)
WHEN NOT MATCHED THEN INSERT (ingredient_code, ingredient_name, base_unit)
VALUES (source.ingredient_code, source.ingredient_name, source.base_unit);

MERGE INTO ingredients target
USING (SELECT 'DEMO-LENTIL' ingredient_code, 'Lentils' ingredient_name, 'KG' base_unit FROM dual) source
ON (target.ingredient_code = source.ingredient_code)
WHEN NOT MATCHED THEN INSERT (ingredient_code, ingredient_name, base_unit)
VALUES (source.ingredient_code, source.ingredient_name, source.base_unit);

MERGE INTO ingredients target
USING (SELECT 'DEMO-MILK' ingredient_code, 'Milk' ingredient_name, 'L' base_unit FROM dual) source
ON (target.ingredient_code = source.ingredient_code)
WHEN NOT MATCHED THEN INSERT (ingredient_code, ingredient_name, base_unit)
VALUES (source.ingredient_code, source.ingredient_name, source.base_unit);

-- The two rules below are synthetic demonstration assumptions, not real contract terms.
-- Proposed amount = eligible discrepancy value x penalty_percent, capped at max_penalty.
MERGE INTO vendor_contract_rules target
USING (
    SELECT v.vendor_id, 'DEMO-SHORT-10' rule_code,
           'Synthetic short-delivery example' rule_name,
           'SHORT_DELIVERY' discrepancy_type,
           DATE '2026-01-01' effective_from, CAST(NULL AS DATE) effective_to,
           0 tolerance_percent, 10 penalty_percent, 1000 max_penalty,
           'Demonstration only: 10 percent of eligible short-delivery value, capped at INR 1000.' terms_text
    FROM vendors v WHERE v.vendor_code = 'DEMO-V001'
) source
ON (target.vendor_id = source.vendor_id AND target.rule_code = source.rule_code)
WHEN NOT MATCHED THEN INSERT (
    vendor_id, rule_code, rule_name, discrepancy_type, effective_from,
    effective_to, tolerance_percent, penalty_percent, max_penalty, terms_text
) VALUES (
    source.vendor_id, source.rule_code, source.rule_name, source.discrepancy_type,
    source.effective_from, source.effective_to, source.tolerance_percent,
    source.penalty_percent, source.max_penalty, source.terms_text
);

MERGE INTO vendor_contract_rules target
USING (
    SELECT v.vendor_id, 'DEMO-REJECT-15' rule_code,
           'Synthetic rejected-supply example' rule_name,
           'REJECTED_SUPPLY' discrepancy_type,
           DATE '2026-01-01' effective_from, CAST(NULL AS DATE) effective_to,
           0 tolerance_percent, 15 penalty_percent, 500 max_penalty,
           'Demonstration only: 15 percent of eligible rejected-supply value, capped at INR 500.' terms_text
    FROM vendors v WHERE v.vendor_code = 'DEMO-V001'
) source
ON (target.vendor_id = source.vendor_id AND target.rule_code = source.rule_code)
WHEN NOT MATCHED THEN INSERT (
    vendor_id, rule_code, rule_name, discrepancy_type, effective_from,
    effective_to, tolerance_percent, penalty_percent, max_penalty, terms_text
) VALUES (
    source.vendor_id, source.rule_code, source.rule_name, source.discrepancy_type,
    source.effective_from, source.effective_to, source.tolerance_percent,
    source.penalty_percent, source.max_penalty, source.terms_text
);

MERGE INTO purchase_orders target
USING (
    SELECT 'DEMO-PO-001' po_number, v.vendor_id, b.block_id,
           DATE '2026-09-01' ordered_at, DATE '2026-09-03' expected_by,
           'PARTIAL' status, 7500 approved_amount
    FROM vendors v CROSS JOIN mess_blocks b
    WHERE v.vendor_code = 'DEMO-V001' AND b.block_code = 'DEMO-BLOCK-A'
) source
ON (target.po_number = source.po_number)
WHEN NOT MATCHED THEN INSERT (
    po_number, vendor_id, block_id, ordered_at, expected_by, status, approved_amount
) VALUES (
    source.po_number, source.vendor_id, source.block_id, source.ordered_at,
    source.expected_by, source.status, source.approved_amount
);

MERGE INTO purchase_orders target
USING (
    SELECT 'DEMO-PO-002' po_number, v.vendor_id, b.block_id,
           DATE '2026-09-02' ordered_at, DATE '2026-09-04' expected_by,
           'CLOSED' status, 2100 approved_amount
    FROM vendors v CROSS JOIN mess_blocks b
    WHERE v.vendor_code = 'DEMO-V002' AND b.block_code = 'DEMO-BLOCK-B'
) source
ON (target.po_number = source.po_number)
WHEN NOT MATCHED THEN INSERT (
    po_number, vendor_id, block_id, ordered_at, expected_by, status, approved_amount
) VALUES (
    source.po_number, source.vendor_id, source.block_id, source.ordered_at,
    source.expected_by, source.status, source.approved_amount
);

MERGE INTO purchase_order_items target
USING (
    SELECT p.po_id, i.ingredient_id, 100 ordered_qty, 52 unit_price
    FROM purchase_orders p CROSS JOIN ingredients i
    WHERE p.po_number = 'DEMO-PO-001' AND i.ingredient_code = 'DEMO-RICE'
) source
ON (target.po_id = source.po_id AND target.ingredient_id = source.ingredient_id)
WHEN NOT MATCHED THEN INSERT (po_id, ingredient_id, ordered_qty, unit_price)
VALUES (source.po_id, source.ingredient_id, source.ordered_qty, source.unit_price);

MERGE INTO purchase_order_items target
USING (
    SELECT p.po_id, i.ingredient_id, 20 ordered_qty, 100 unit_price
    FROM purchase_orders p CROSS JOIN ingredients i
    WHERE p.po_number = 'DEMO-PO-001' AND i.ingredient_code = 'DEMO-LENTIL'
) source
ON (target.po_id = source.po_id AND target.ingredient_id = source.ingredient_id)
WHEN NOT MATCHED THEN INSERT (po_id, ingredient_id, ordered_qty, unit_price)
VALUES (source.po_id, source.ingredient_id, source.ordered_qty, source.unit_price);

MERGE INTO purchase_order_items target
USING (
    SELECT p.po_id, i.ingredient_id, 60 ordered_qty, 35 unit_price
    FROM purchase_orders p CROSS JOIN ingredients i
    WHERE p.po_number = 'DEMO-PO-002' AND i.ingredient_code = 'DEMO-MILK'
) source
ON (target.po_id = source.po_id AND target.ingredient_id = source.ingredient_id)
WHEN NOT MATCHED THEN INSERT (po_id, ingredient_id, ordered_qty, unit_price)
VALUES (source.po_id, source.ingredient_id, source.ordered_qty, source.unit_price);

MERGE INTO delivery_receipts target
USING (
    SELECT p.po_id, 'DEMO-INV-001' invoice_reference, 4004 invoice_amount,
           TIMESTAMP '2026-09-02 09:00:00' arrived_at,
           'Demo Mess Auditor' verified_by,
           TIMESTAMP '2026-09-02 10:00:00' verified_at,
           CAST(NULL AS TIMESTAMP) inventory_processed_at,
           'Synthetic partial delivery; 3 kg rejected.' notes
    FROM purchase_orders p WHERE p.po_number = 'DEMO-PO-001'
) source
ON (target.po_id = source.po_id AND target.invoice_reference = source.invoice_reference)
WHEN NOT MATCHED THEN INSERT (
    po_id, invoice_reference, invoice_amount, arrived_at, verified_by,
    verified_at, inventory_processed_at, notes
) VALUES (
    source.po_id, source.invoice_reference, source.invoice_amount,
    source.arrived_at, source.verified_by, source.verified_at,
    source.inventory_processed_at, source.notes
);

MERGE INTO delivery_receipts target
USING (
    SELECT p.po_id, 'DEMO-INV-002' invoice_reference, 2100 invoice_amount,
           TIMESTAMP '2026-09-03 09:00:00' arrived_at,
           'Demo Mess Auditor' verified_by,
           TIMESTAMP '2026-09-03 10:00:00' verified_at,
           TIMESTAMP '2026-09-03 10:05:00' inventory_processed_at,
           'Synthetic complete milk delivery.' notes
    FROM purchase_orders p WHERE p.po_number = 'DEMO-PO-002'
) source
ON (target.po_id = source.po_id AND target.invoice_reference = source.invoice_reference)
WHEN NOT MATCHED THEN INSERT (
    po_id, invoice_reference, invoice_amount, arrived_at, verified_by,
    verified_at, inventory_processed_at, notes
) VALUES (
    source.po_id, source.invoice_reference, source.invoice_amount,
    source.arrived_at, source.verified_by, source.verified_at,
    source.inventory_processed_at, source.notes
);

MERGE INTO delivery_receipt_items target
USING (
    SELECT r.receipt_id, r.po_id, oi.po_item_id, 80 received_qty,
           77 accepted_qty, 3 rejected_qty, 'Synthetic rice count.' notes
    FROM delivery_receipts r
    JOIN purchase_orders p ON p.po_id = r.po_id
    JOIN purchase_order_items oi ON oi.po_id = p.po_id
    JOIN ingredients i ON i.ingredient_id = oi.ingredient_id
    WHERE p.po_number = 'DEMO-PO-001'
      AND r.invoice_reference = 'DEMO-INV-001'
      AND i.ingredient_code = 'DEMO-RICE'
) source
ON (target.receipt_id = source.receipt_id AND target.po_item_id = source.po_item_id)
WHEN NOT MATCHED THEN INSERT (
    receipt_id, po_id, po_item_id, received_qty, accepted_qty, rejected_qty, notes
) VALUES (
    source.receipt_id, source.po_id, source.po_item_id, source.received_qty,
    source.accepted_qty, source.rejected_qty, source.notes
);

MERGE INTO delivery_receipt_items target
USING (
    SELECT r.receipt_id, r.po_id, oi.po_item_id, 60 received_qty,
           60 accepted_qty, 0 rejected_qty, 'Synthetic milk count.' notes
    FROM delivery_receipts r
    JOIN purchase_orders p ON p.po_id = r.po_id
    JOIN purchase_order_items oi ON oi.po_id = p.po_id
    JOIN ingredients i ON i.ingredient_id = oi.ingredient_id
    WHERE p.po_number = 'DEMO-PO-002'
      AND r.invoice_reference = 'DEMO-INV-002'
      AND i.ingredient_code = 'DEMO-MILK'
) source
ON (target.receipt_id = source.receipt_id AND target.po_item_id = source.po_item_id)
WHEN NOT MATCHED THEN INSERT (
    receipt_id, po_id, po_item_id, received_qty, accepted_qty, rejected_qty, notes
) VALUES (
    source.receipt_id, source.po_id, source.po_item_id, source.received_qty,
    source.accepted_qty, source.rejected_qty, source.notes
);

MERGE INTO inventory_balances target
USING (
    SELECT b.block_id, i.ingredient_id, 20 on_hand_qty, 25 reorder_level
    FROM mess_blocks b CROSS JOIN ingredients i
    WHERE b.block_code = 'DEMO-BLOCK-A' AND i.ingredient_code = 'DEMO-RICE'
    UNION ALL
    SELECT b.block_id, i.ingredient_id, 5 on_hand_qty, 10 reorder_level
    FROM mess_blocks b CROSS JOIN ingredients i
    WHERE b.block_code = 'DEMO-BLOCK-A' AND i.ingredient_code = 'DEMO-LENTIL'
    UNION ALL
    SELECT b.block_id, i.ingredient_id, 70 on_hand_qty, 20 reorder_level
    FROM mess_blocks b CROSS JOIN ingredients i
    WHERE b.block_code = 'DEMO-BLOCK-B' AND i.ingredient_code = 'DEMO-MILK'
) source
ON (target.block_id = source.block_id AND target.ingredient_id = source.ingredient_id)
WHEN NOT MATCHED THEN INSERT (block_id, ingredient_id, on_hand_qty, reorder_level)
VALUES (source.block_id, source.ingredient_id, source.on_hand_qty, source.reorder_level);

MERGE INTO inventory_transactions target
USING (
    SELECT b.block_id, i.ingredient_id, 'DEMO-OPEN-RICE-A' transaction_reference,
           'OPENING' transaction_type, 20 quantity_delta, 20 balance_after_qty
    FROM mess_blocks b CROSS JOIN ingredients i
    WHERE b.block_code = 'DEMO-BLOCK-A' AND i.ingredient_code = 'DEMO-RICE'
    UNION ALL
    SELECT b.block_id, i.ingredient_id, 'DEMO-OPEN-LENTIL-A' transaction_reference,
           'OPENING' transaction_type, 5 quantity_delta, 5 balance_after_qty
    FROM mess_blocks b CROSS JOIN ingredients i
    WHERE b.block_code = 'DEMO-BLOCK-A' AND i.ingredient_code = 'DEMO-LENTIL'
    UNION ALL
    SELECT b.block_id, i.ingredient_id, 'DEMO-OPEN-MILK-B' transaction_reference,
           'OPENING' transaction_type, 10 quantity_delta, 10 balance_after_qty
    FROM mess_blocks b CROSS JOIN ingredients i
    WHERE b.block_code = 'DEMO-BLOCK-B' AND i.ingredient_code = 'DEMO-MILK'
) source
ON (target.transaction_reference = source.transaction_reference)
WHEN NOT MATCHED THEN INSERT (
    transaction_reference, block_id, ingredient_id, transaction_type,
    quantity_delta, balance_after_qty
) VALUES (
    source.transaction_reference, source.block_id, source.ingredient_id,
    source.transaction_type, source.quantity_delta, source.balance_after_qty
);

MERGE INTO inventory_transactions target
USING (
    SELECT b.block_id, i.ingredient_id,
           'DEMO-RECEIPT-INV-002-MILK' transaction_reference,
           'RECEIPT' transaction_type, 60 quantity_delta, 70 balance_after_qty,
           dri.receipt_item_id source_receipt_item_id
    FROM delivery_receipt_items dri
    JOIN delivery_receipts r ON r.receipt_id = dri.receipt_id
    JOIN purchase_orders p ON p.po_id = dri.po_id
    JOIN purchase_order_items oi ON oi.po_id = dri.po_id AND oi.po_item_id = dri.po_item_id
    JOIN ingredients i ON i.ingredient_id = oi.ingredient_id
    JOIN mess_blocks b ON b.block_id = p.block_id
    WHERE p.po_number = 'DEMO-PO-002'
      AND r.invoice_reference = 'DEMO-INV-002'
      AND i.ingredient_code = 'DEMO-MILK'
) source
ON (target.transaction_reference = source.transaction_reference)
WHEN NOT MATCHED THEN INSERT (
    transaction_reference, block_id, ingredient_id, transaction_type,
    quantity_delta, balance_after_qty, source_receipt_item_id
) VALUES (
    source.transaction_reference, source.block_id, source.ingredient_id,
    source.transaction_type, source.quantity_delta, source.balance_after_qty,
    source.source_receipt_item_id
);

MERGE INTO daily_mess_records target
USING (
    SELECT b.block_id, DATE '2026-09-01' record_date, 420 meals_served,
           78 prepared_kg, 8.4 leftover_kg,
           'Edible food remaining in designated return containers, weighed after service.' measurement_method,
           'Synthetic measured example.' notes
    FROM mess_blocks b WHERE b.block_code = 'DEMO-BLOCK-A'
) source
ON (target.block_id = source.block_id AND target.record_date = source.record_date)
WHEN NOT MATCHED THEN INSERT (
    block_id, record_date, meals_served, prepared_kg, leftover_kg, measurement_method, notes
) VALUES (
    source.block_id, source.record_date, source.meals_served, source.prepared_kg,
    source.leftover_kg, source.measurement_method, source.notes
);

MERGE INTO daily_mess_records target
USING (
    SELECT b.block_id, DATE '2026-09-02' record_date, 400 meals_served,
           75 prepared_kg, CAST(NULL AS NUMBER(12,3)) leftover_kg,
           CAST(NULL AS VARCHAR2(200)) measurement_method,
           'Synthetic example with missing leftover measurement.' notes
    FROM mess_blocks b WHERE b.block_code = 'DEMO-BLOCK-A'
) source
ON (target.block_id = source.block_id AND target.record_date = source.record_date)
WHEN NOT MATCHED THEN INSERT (
    block_id, record_date, meals_served, prepared_kg, leftover_kg, measurement_method, notes
) VALUES (
    source.block_id, source.record_date, source.meals_served, source.prepared_kg,
    source.leftover_kg, source.measurement_method, source.notes
);

MERGE INTO daily_mess_records target
USING (
    SELECT b.block_id, DATE '2026-09-01' record_date, 0 meals_served,
           CAST(NULL AS NUMBER(12,3)) prepared_kg,
           CAST(NULL AS NUMBER(12,3)) leftover_kg,
           CAST(NULL AS VARCHAR2(200)) measurement_method,
           'Synthetic zero-meal example; per-capita result is not available.' notes
    FROM mess_blocks b WHERE b.block_code = 'DEMO-BLOCK-B'
) source
ON (target.block_id = source.block_id AND target.record_date = source.record_date)
WHEN NOT MATCHED THEN INSERT (
    block_id, record_date, meals_served, prepared_kg, leftover_kg, measurement_method, notes
) VALUES (
    source.block_id, source.record_date, source.meals_served, source.prepared_kg,
    source.leftover_kg, source.measurement_method, source.notes
);

MERGE INTO daily_mess_records target
USING (
    SELECT b.block_id, DATE '2026-10-01' record_date, 200 meals_served,
           34 prepared_kg, 2.5 leftover_kg,
           'Edible food remaining in designated return containers, weighed after service.' measurement_method,
           'Synthetic measured example.' notes
    FROM mess_blocks b WHERE b.block_code = 'DEMO-BLOCK-B'
) source
ON (target.block_id = source.block_id AND target.record_date = source.record_date)
WHEN NOT MATCHED THEN INSERT (
    block_id, record_date, meals_served, prepared_kg, leftover_kg, measurement_method, notes
) VALUES (
    source.block_id, source.record_date, source.meals_served, source.prepared_kg,
    source.leftover_kg, source.measurement_method, source.notes
);

COMMIT;
