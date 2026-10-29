-- Read-only preflight. Run this first using your existing FreeSQL connection.
-- CREATE SESSION is already demonstrated if this query returns successfully.
-- This reports currently enabled privileges, not an actual CREATE TABLE attempt.

SELECT privilege AS enabled_privilege
FROM session_privs
WHERE privilege IN (
    'CREATE TABLE',
    'CREATE PROCEDURE',
    'CREATE TRIGGER',
    'UNLIMITED TABLESPACE'
)
ORDER BY privilege;

-- The table owner needs quota on the default tablespace or UNLIMITED TABLESPACE.
SELECT default_tablespace
FROM user_users;

SELECT u.default_tablespace,
       CASE
           WHEN EXISTS (
               SELECT 1 FROM session_privs
               WHERE privilege = 'UNLIMITED TABLESPACE'
           ) THEN 'UNLIMITED TABLESPACE ENABLED'
           WHEN q.max_bytes = -1 THEN 'UNLIMITED QUOTA'
           WHEN q.max_bytes > q.bytes THEN 'POSITIVE QUOTA REMAINS'
           ELSE 'NO VISIBLE QUOTA ON DEFAULT TABLESPACE'
       END quota_status
FROM user_users u
LEFT JOIN user_ts_quotas q
       ON q.tablespace_name = u.default_tablespace;

SELECT tablespace_name, bytes, max_bytes
FROM user_ts_quotas
ORDER BY tablespace_name;

-- If this returns any names, stop and review existing objects before running DDL.
SELECT object_name, object_type
FROM user_objects
WHERE object_name IN (
    'VENDORS',
    'MESS_BLOCKS',
    'INGREDIENTS',
    'PURCHASE_ORDERS',
    'PURCHASE_ORDER_ITEMS',
    'DELIVERY_RECEIPTS',
    'DELIVERY_RECEIPT_ITEMS',
    'INVENTORY_BALANCES',
    'INVENTORY_TRANSACTIONS',
    'DAILY_MESS_RECORDS',
    'VENDOR_CONTRACT_RULES',
    'VENDOR_PENALTY_REVIEWS',
    'AUDIT_LOG'
)
ORDER BY object_name, object_type;
