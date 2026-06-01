ALTER TABLE order_delivery
    ADD COLUMN fulfilled_at          DATETIME    NULL,
    ADD COLUMN customer_confirmed_at DATETIME    NULL,
    ADD COLUMN auto_confirmed        BOOLEAN     NOT NULL DEFAULT FALSE;