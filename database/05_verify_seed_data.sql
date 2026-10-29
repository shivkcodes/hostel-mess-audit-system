SELECT 'VENDORS' entity_name, COUNT(*) row_count
FROM vendors
WHERE vendor_code LIKE 'DEMO-%'
UNION ALL
SELECT 'MESS_BLOCKS', COUNT(*)
FROM mess_blocks
WHERE block_code LIKE 'DEMO-%'
UNION ALL
SELECT 'INGREDIENTS', COUNT(*)
FROM ingredients
WHERE ingredient_code LIKE 'DEMO-%'
UNION ALL
SELECT 'PURCHASE_ORDERS', COUNT(*)
FROM purchase_orders
WHERE po_number LIKE 'DEMO-%'
UNION ALL
SELECT 'PURCHASE_ORDER_ITEMS', COUNT(*)
FROM purchase_order_items poi
JOIN purchase_orders po ON po.po_id = poi.po_id
WHERE po.po_number LIKE 'DEMO-%'
UNION ALL
SELECT 'DELIVERY_RECEIPTS', COUNT(*)
FROM delivery_receipts dr
JOIN purchase_orders po ON po.po_id = dr.po_id
WHERE po.po_number LIKE 'DEMO-%'
UNION ALL
SELECT 'DELIVERY_RECEIPT_ITEMS', COUNT(*)
FROM delivery_receipt_items dri
JOIN purchase_orders po ON po.po_id = dri.po_id
WHERE po.po_number LIKE 'DEMO-%'
UNION ALL
SELECT 'DAILY_MESS_RECORDS', COUNT(*)
FROM daily_mess_records d
JOIN mess_blocks b ON b.block_id = d.block_id
WHERE b.block_code LIKE 'DEMO-%'
ORDER BY entity_name;




SELECT po.po_number,
       i.ingredient_code,
       i.base_unit,
       poi.ordered_qty,
       NVL(SUM(dri.received_qty), 0) received_qty,
       NVL(SUM(dri.accepted_qty), 0) accepted_qty,
       NVL(SUM(dri.rejected_qty), 0) rejected_qty,
       GREATEST(poi.ordered_qty - NVL(SUM(dri.received_qty), 0), 0) short_qty
FROM purchase_orders po
JOIN purchase_order_items poi ON poi.po_id = po.po_id
JOIN ingredients i ON i.ingredient_id = poi.ingredient_id
LEFT JOIN delivery_receipt_items dri
       ON dri.po_id = poi.po_id
      AND dri.po_item_id = poi.po_item_id
WHERE po.po_number = 'DEMO-PO-001'
  AND i.ingredient_code = 'DEMO-RICE'
GROUP BY po.po_number, i.ingredient_code, i.base_unit, poi.ordered_qty;




SELECT r.invoice_reference,
       r.invoice_amount,
       ROUND(NVL(SUM(dri.accepted_qty * poi.unit_price), 0), 2) expected_accepted_amount,
       CASE
           WHEN ABS(
               r.invoice_amount
               - ROUND(NVL(SUM(dri.accepted_qty * poi.unit_price), 0), 2)
           ) <= 0.01
           THEN 'MATCH'
           ELSE 'REVIEW'
       END invoice_check
FROM delivery_receipts r
JOIN purchase_orders po ON po.po_id = r.po_id
LEFT JOIN delivery_receipt_items dri ON dri.receipt_id = r.receipt_id
LEFT JOIN purchase_order_items poi
       ON poi.po_id = dri.po_id
      AND poi.po_item_id = dri.po_item_id
WHERE po.po_number LIKE 'DEMO-%'
GROUP BY r.invoice_reference, r.invoice_amount
ORDER BY r.invoice_reference;




SELECT b.block_code,
       i.ingredient_code,
       i.base_unit,
       ib.on_hand_qty,
       ib.reorder_level
FROM inventory_balances ib
JOIN mess_blocks b ON b.block_id = ib.block_id
JOIN ingredients i ON i.ingredient_id = ib.ingredient_id
WHERE b.block_code LIKE 'DEMO-%'
  AND ib.on_hand_qty < ib.reorder_level
ORDER BY b.block_code, i.ingredient_code;




SELECT b.block_code,
       d.record_date,
       d.meals_served,
       d.leftover_kg,
       CASE
           WHEN d.meals_served = 0 THEN 'ZERO_MEALS'
           ELSE 'MEALS_SERVED'
       END meal_status,
       CASE
           WHEN d.leftover_kg IS NULL THEN 'MISSING_MEASUREMENT'
           ELSE 'MEASURED'
       END measurement_status,
       CASE
           WHEN d.meals_served > 0 AND d.leftover_kg IS NOT NULL
           THEN ROUND(d.leftover_kg / NULLIF(d.meals_served, 0), 4)
           ELSE NULL
       END per_capita_leftover_kg
FROM daily_mess_records d
JOIN mess_blocks b ON b.block_id = d.block_id
WHERE b.block_code LIKE 'DEMO-%'
ORDER BY b.block_code, d.record_date;





SELECT * FROM DELIVERY_RECEIPTS ORDER BY arrived_at DESC;

SELECT * FROM DELIVERY_RECEIPT_ITEMS;

SELECT * FROM INVENTORY_TRANSACTIONS;

SELECT * FROM INVENTORY_BALANCES;

SELECT
    ri.RECEIPT_ITEM_ID,
    ri.PO_ITEM_ID,
    ri.RECEIVED_QTY,
    ri.ACCEPTED_QTY,
    ri.REJECTED_QTY,
    it.QUANTITY_DELTA,
    it.SOURCE_RECEIPT_ITEM_ID,
    it.TRANSACTION_REFERENCE
FROM DELIVERY_RECEIPT_ITEMS ri
LEFT JOIN INVENTORY_TRANSACTIONS it
    ON it.SOURCE_RECEIPT_ITEM_ID = ri.RECEIPT_ITEM_ID
WHERE ri.RECEIPT_ID = 21
ORDER BY ri.RECEIPT_ITEM_ID;

SELECT
    PO_ID,
    PO_NUMBER,
    STATUS
FROM PURCHASE_ORDERS
WHERE PO_ID = 21;

SELECT table_name
FROM user_tables
WHERE table_name = 'APP_USERS';

SELECT USER_ID, USERNAME, ROLE, ACTIVE_FLAG
FROM APP_USERS
ORDER BY USER_ID;

SELECT COLUMN_ID, COLUMN_NAME, DATA_TYPE, NULLABLE
FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'APP_USERS'
ORDER BY COLUMN_ID;

SELECT USER_ID, USERNAME, ROLE_CODE, ACTIVE_FLAG
FROM APP_USERS
ORDER BY USER_ID;


SELECT table_name
FROM user_tables
WHERE table_name = 'USER_PROFILES';

SELECT constraint_name, constraint_type, status
FROM user_constraints
WHERE table_name = 'USER_PROFILES'
ORDER BY constraint_name;