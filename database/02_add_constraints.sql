-- Adds primary keys, unique keys, checks, and foreign keys to the empty tables.
-- Run once, after 01_create_tables.sql succeeds.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

ALTER TABLE vendors ADD CONSTRAINT pk_vendors PRIMARY KEY (vendor_id);
ALTER TABLE vendors ADD CONSTRAINT uq_vendors_code UNIQUE (vendor_code);
ALTER TABLE vendors ADD CONSTRAINT ck_vendors_active CHECK (active_flag IN ('Y', 'N'));

ALTER TABLE mess_blocks ADD CONSTRAINT pk_mess_blocks PRIMARY KEY (block_id);
ALTER TABLE mess_blocks ADD CONSTRAINT uq_mess_blocks_code UNIQUE (block_code);
ALTER TABLE mess_blocks ADD CONSTRAINT ck_mess_blocks_active CHECK (active_flag IN ('Y', 'N'));

ALTER TABLE ingredients ADD CONSTRAINT pk_ingredients PRIMARY KEY (ingredient_id);
ALTER TABLE ingredients ADD CONSTRAINT uq_ingredients_code UNIQUE (ingredient_code);
ALTER TABLE ingredients ADD CONSTRAINT ck_ingredients_unit CHECK (base_unit IN ('KG', 'L', 'EACH'));
ALTER TABLE ingredients ADD CONSTRAINT ck_ingredients_active CHECK (active_flag IN ('Y', 'N'));

ALTER TABLE purchase_orders ADD CONSTRAINT pk_purchase_orders PRIMARY KEY (po_id);
ALTER TABLE purchase_orders ADD CONSTRAINT uq_purchase_orders_number UNIQUE (po_number);
ALTER TABLE purchase_orders ADD CONSTRAINT ck_po_status CHECK (status IN ('OPEN', 'PARTIAL', 'CLOSED', 'CANCELLED'));
ALTER TABLE purchase_orders ADD CONSTRAINT ck_po_approved_amount CHECK (approved_amount >= 0);
ALTER TABLE purchase_orders ADD CONSTRAINT ck_po_dates CHECK (expected_by IS NULL OR expected_by >= ordered_at);
ALTER TABLE purchase_orders ADD CONSTRAINT fk_po_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (vendor_id);
ALTER TABLE purchase_orders ADD CONSTRAINT fk_po_block FOREIGN KEY (block_id) REFERENCES mess_blocks (block_id);

ALTER TABLE purchase_order_items ADD CONSTRAINT pk_po_items PRIMARY KEY (po_item_id);
ALTER TABLE purchase_order_items ADD CONSTRAINT uq_po_item_ingredient UNIQUE (po_id, ingredient_id);
ALTER TABLE purchase_order_items ADD CONSTRAINT uq_po_item_pair UNIQUE (po_id, po_item_id);
ALTER TABLE purchase_order_items ADD CONSTRAINT ck_po_item_qty CHECK (ordered_qty > 0);
ALTER TABLE purchase_order_items ADD CONSTRAINT ck_po_item_price CHECK (unit_price >= 0);
ALTER TABLE purchase_order_items ADD CONSTRAINT fk_poi_order FOREIGN KEY (po_id) REFERENCES purchase_orders (po_id);
ALTER TABLE purchase_order_items ADD CONSTRAINT fk_poi_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (ingredient_id);

ALTER TABLE delivery_receipts ADD CONSTRAINT pk_delivery_receipts PRIMARY KEY (receipt_id);
ALTER TABLE delivery_receipts ADD CONSTRAINT uq_receipt_invoice UNIQUE (po_id, invoice_reference);
ALTER TABLE delivery_receipts ADD CONSTRAINT uq_receipt_order_pair UNIQUE (receipt_id, po_id);
ALTER TABLE delivery_receipts ADD CONSTRAINT ck_receipt_invoice_amt CHECK (invoice_amount >= 0);
ALTER TABLE delivery_receipts ADD CONSTRAINT ck_receipt_verified CHECK (
    (verified_by IS NULL AND verified_at IS NULL)
    OR (verified_by IS NOT NULL AND verified_at IS NOT NULL)
);
ALTER TABLE delivery_receipts ADD CONSTRAINT ck_receipt_processed CHECK (
    inventory_processed_at IS NULL OR verified_at IS NOT NULL
);
ALTER TABLE delivery_receipts ADD CONSTRAINT fk_receipt_order FOREIGN KEY (po_id) REFERENCES purchase_orders (po_id);

ALTER TABLE delivery_receipt_items ADD CONSTRAINT pk_delivery_receipt_items PRIMARY KEY (receipt_item_id);
ALTER TABLE delivery_receipt_items ADD CONSTRAINT uq_receipt_po_item UNIQUE (receipt_id, po_item_id);
ALTER TABLE delivery_receipt_items ADD CONSTRAINT ck_receipt_item_qty CHECK (
    received_qty >= 0 AND accepted_qty >= 0 AND rejected_qty >= 0
);
ALTER TABLE delivery_receipt_items ADD CONSTRAINT ck_receipt_item_split CHECK (
    received_qty = accepted_qty + rejected_qty
);
ALTER TABLE delivery_receipt_items ADD CONSTRAINT fk_dri_receipt_po FOREIGN KEY (receipt_id, po_id)
    REFERENCES delivery_receipts (receipt_id, po_id);
ALTER TABLE delivery_receipt_items ADD CONSTRAINT fk_dri_po_item FOREIGN KEY (po_id, po_item_id)
    REFERENCES purchase_order_items (po_id, po_item_id);

ALTER TABLE inventory_balances ADD CONSTRAINT pk_inventory_balances PRIMARY KEY (block_id, ingredient_id);
ALTER TABLE inventory_balances ADD CONSTRAINT ck_inventory_balance_qty CHECK (on_hand_qty >= 0);
ALTER TABLE inventory_balances ADD CONSTRAINT ck_inventory_reorder_qty CHECK (reorder_level >= 0);
ALTER TABLE inventory_balances ADD CONSTRAINT fk_ib_block FOREIGN KEY (block_id) REFERENCES mess_blocks (block_id);
ALTER TABLE inventory_balances ADD CONSTRAINT fk_ib_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (ingredient_id);

ALTER TABLE inventory_transactions ADD CONSTRAINT pk_inventory_transactions PRIMARY KEY (transaction_id);
ALTER TABLE inventory_transactions ADD CONSTRAINT uq_inventory_txn_ref UNIQUE (transaction_reference);
ALTER TABLE inventory_transactions ADD CONSTRAINT uq_inventory_txn_receipt UNIQUE (source_receipt_item_id);
ALTER TABLE inventory_transactions ADD CONSTRAINT ck_inventory_txn_type CHECK (
    transaction_type IN ('OPENING', 'RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT')
);
ALTER TABLE inventory_transactions ADD CONSTRAINT ck_inventory_txn_delta CHECK (
    (transaction_type IN ('OPENING', 'RECEIPT', 'ADJUSTMENT_IN') AND quantity_delta > 0)
    OR (transaction_type IN ('ISSUE', 'ADJUSTMENT_OUT') AND quantity_delta < 0)
);
ALTER TABLE inventory_transactions ADD CONSTRAINT ck_inventory_txn_balance CHECK (balance_after_qty >= 0);
ALTER TABLE inventory_transactions ADD CONSTRAINT ck_inventory_txn_source CHECK (
    (transaction_type = 'RECEIPT' AND source_receipt_item_id IS NOT NULL)
    OR (transaction_type <> 'RECEIPT' AND source_receipt_item_id IS NULL)
);
ALTER TABLE inventory_transactions ADD CONSTRAINT fk_it_block FOREIGN KEY (block_id) REFERENCES mess_blocks (block_id);
ALTER TABLE inventory_transactions ADD CONSTRAINT fk_it_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (ingredient_id);
ALTER TABLE inventory_transactions ADD CONSTRAINT fk_it_receipt_item FOREIGN KEY (source_receipt_item_id)
    REFERENCES delivery_receipt_items (receipt_item_id);

ALTER TABLE daily_mess_records ADD CONSTRAINT pk_daily_mess_records PRIMARY KEY (record_id);
ALTER TABLE daily_mess_records ADD CONSTRAINT uq_daily_block_date UNIQUE (block_id, record_date);
ALTER TABLE daily_mess_records ADD CONSTRAINT ck_daily_meals CHECK (meals_served >= 0);
ALTER TABLE daily_mess_records ADD CONSTRAINT ck_daily_prepared CHECK (prepared_kg IS NULL OR prepared_kg >= 0);
ALTER TABLE daily_mess_records ADD CONSTRAINT ck_daily_leftover CHECK (leftover_kg IS NULL OR leftover_kg >= 0);
ALTER TABLE daily_mess_records ADD CONSTRAINT ck_daily_leftover_limit CHECK (
    leftover_kg IS NULL OR prepared_kg IS NULL OR leftover_kg <= prepared_kg
);
ALTER TABLE daily_mess_records ADD CONSTRAINT ck_daily_method CHECK (
    leftover_kg IS NULL OR measurement_method IS NOT NULL
);
ALTER TABLE daily_mess_records ADD CONSTRAINT fk_daily_block FOREIGN KEY (block_id) REFERENCES mess_blocks (block_id);

ALTER TABLE vendor_contract_rules ADD CONSTRAINT pk_vendor_contract_rules PRIMARY KEY (rule_id);
ALTER TABLE vendor_contract_rules ADD CONSTRAINT uq_vendor_rule_code UNIQUE (vendor_id, rule_code);
ALTER TABLE vendor_contract_rules ADD CONSTRAINT ck_rule_type CHECK (
    discrepancy_type IN ('SHORT_DELIVERY', 'REJECTED_SUPPLY')
);
ALTER TABLE vendor_contract_rules ADD CONSTRAINT ck_rule_tolerance CHECK (
    tolerance_percent BETWEEN 0 AND 100
);
ALTER TABLE vendor_contract_rules ADD CONSTRAINT ck_rule_penalty_pct CHECK (
    penalty_percent BETWEEN 0 AND 100
);
ALTER TABLE vendor_contract_rules ADD CONSTRAINT ck_rule_cap CHECK (max_penalty IS NULL OR max_penalty >= 0);
ALTER TABLE vendor_contract_rules ADD CONSTRAINT ck_rule_dates CHECK (
    effective_to IS NULL OR effective_to >= effective_from
);
ALTER TABLE vendor_contract_rules ADD CONSTRAINT fk_rule_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (vendor_id);

ALTER TABLE vendor_penalty_reviews ADD CONSTRAINT pk_vendor_penalty_reviews PRIMARY KEY (review_id);
ALTER TABLE vendor_penalty_reviews ADD CONSTRAINT uq_review_po_rule UNIQUE (po_id, rule_id);
ALTER TABLE vendor_penalty_reviews ADD CONSTRAINT ck_review_amounts CHECK (
    eligible_line_count >= 0 AND eligible_value >= 0 AND proposed_amount >= 0
    AND (approved_amount IS NULL OR approved_amount >= 0)
);
ALTER TABLE vendor_penalty_reviews ADD CONSTRAINT ck_review_status CHECK (
    status IN ('PROPOSED', 'APPROVED', 'REJECTED')
);
ALTER TABLE vendor_penalty_reviews ADD CONSTRAINT ck_review_decision CHECK (
    (status = 'PROPOSED' AND approved_amount IS NULL AND reviewed_by IS NULL AND reviewed_at IS NULL)
    OR (status = 'APPROVED' AND approved_amount IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    OR (status = 'REJECTED' AND approved_amount IS NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
);
ALTER TABLE vendor_penalty_reviews ADD CONSTRAINT fk_review_order FOREIGN KEY (po_id) REFERENCES purchase_orders (po_id);
ALTER TABLE vendor_penalty_reviews ADD CONSTRAINT fk_review_rule FOREIGN KEY (rule_id) REFERENCES vendor_contract_rules (rule_id);

ALTER TABLE audit_log ADD CONSTRAINT pk_audit_log PRIMARY KEY (audit_id);
ALTER TABLE audit_log ADD CONSTRAINT ck_audit_operation CHECK (operation_type IN ('I', 'U', 'D'));
