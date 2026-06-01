ALTER TABLE order_delivery
    ADD COLUMN dispute_reason VARCHAR(255) NULL,
    ADD COLUMN dispute_resolved_at TIMESTAMP NULL;
