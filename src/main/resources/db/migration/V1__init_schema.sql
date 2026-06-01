-- produce_category
CREATE TABLE produce_category (
    id                  CHAR(36) PRIMARY KEY,
    name                VARCHAR(100) NOT NULL UNIQUE,
    refund_window_days  INT NOT NULL DEFAULT 3,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- users
CREATE TABLE users (
    id              CHAR(36) PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- produce
CREATE TABLE produce (
    id          CHAR(36) PRIMARY KEY,
    category_id CHAR(36) NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    unit        VARCHAR(10) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES produce_category(id)
);

-- harvest_batch
CREATE TABLE harvest_batch (
    id                  CHAR(36) PRIMARY KEY,
    produce_id          CHAR(36) NOT NULL,
    quantity_original   DECIMAL(10,2) NOT NULL,
    quantity_available  DECIMAL(10,2) NOT NULL,
    harvested_at        DATE NOT NULL,
    expiry_date         DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (produce_id) REFERENCES produce(id)
);

-- listing
CREATE TABLE listing (
    id                      CHAR(36) PRIMARY KEY,
    batch_id                CHAR(36) NOT NULL,
    retail_price            DECIMAL(10,2) NOT NULL,
    bulk_price              DECIMAL(10,2) NOT NULL,
    min_bulk_quantity       DECIMAL(10,2) NOT NULL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'CAD',
    low_stock_threshold_pct INT NOT NULL DEFAULT 25,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES harvest_batch(id)
);

-- cart
CREATE TABLE cart (
    id          CHAR(36) PRIMARY KEY,
    user_id     CHAR(36) NOT NULL UNIQUE,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- cart_item
CREATE TABLE cart_item (
    id          CHAR(36) PRIMARY KEY,
    cart_id     CHAR(36) NOT NULL,
    listing_id  CHAR(36) NOT NULL,
    quantity    DECIMAL(10,2) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (cart_id) REFERENCES cart(id) ON DELETE CASCADE,
    FOREIGN KEY (listing_id) REFERENCES listing(id),
    CONSTRAINT uq_listing_per_cart UNIQUE (cart_id, listing_id)
);

-- orders
CREATE TABLE orders (
    id                  CHAR(36) PRIMARY KEY,
    user_id             CHAR(36) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount        DECIMAL(10,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'CAD',
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    pricing_type        VARCHAR(10) NOT NULL,
    cancellation_reason TEXT,
    refund_status       VARCHAR(20) NOT NULL DEFAULT 'NONE',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- order_item
CREATE TABLE order_item (
    id                  CHAR(36) PRIMARY KEY,
    order_id            CHAR(36) NOT NULL,
    listing_id          CHAR(36) NOT NULL,
    batch_id            CHAR(36) NOT NULL,
    quantity            DECIMAL(10,2) NOT NULL,
    price_at_purchase   DECIMAL(10,2) NOT NULL,
    pricing_type        VARCHAR(10) NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (listing_id) REFERENCES listing(id),
    FOREIGN KEY (batch_id) REFERENCES harvest_batch(id)
);

-- reservation
CREATE TABLE reservation (
    id          CHAR(36) PRIMARY KEY,
    order_id    CHAR(36) NOT NULL,
    user_id     CHAR(36) NOT NULL,
    batch_id    CHAR(36) NOT NULL,
    quantity    DECIMAL(10,2) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at  DATETIME NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active_key  VARCHAR(73) GENERATED ALWAYS AS (
                    CASE
                        WHEN status = 'ACTIVE'
                        THEN CONCAT(user_id, '_', batch_id)
                        ELSE NULL
                    END
                ) STORED,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (batch_id) REFERENCES harvest_batch(id),
    UNIQUE KEY uq_active_reservation (active_key)
);

-- delivery_slot
CREATE TABLE delivery_slot (
    id              CHAR(36) PRIMARY KEY,
    slot_date       DATE NOT NULL,
    slot_type       VARCHAR(10) NOT NULL,
    capacity        INT NOT NULL DEFAULT 10,
    booked_count    INT NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_slot_per_date UNIQUE (slot_date, slot_type)
);

-- order_delivery
CREATE TABLE order_delivery (
    id               CHAR(36) PRIMARY KEY,
    order_id         CHAR(36) NOT NULL UNIQUE,
    delivery_slot_id CHAR(36) NOT NULL,
    address_line_1   VARCHAR(255) NOT NULL,
    address_line_2   VARCHAR(255),
    city             VARCHAR(100) NOT NULL,
    province         VARCHAR(100) NOT NULL,
    postal_code      VARCHAR(20) NOT NULL,
    country          VARCHAR(3) NOT NULL DEFAULT 'CA',
    delivery_notes   TEXT,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (delivery_slot_id) REFERENCES delivery_slot(id)
);

-- payment
CREATE TABLE payment (
    id                  CHAR(36) PRIMARY KEY,
    order_id            CHAR(36) NOT NULL,
    provider            VARCHAR(20) NOT NULL,
    provider_payment_id VARCHAR(255) NOT NULL,
    provider_event_id   VARCHAR(255) UNIQUE,
    amount              DECIMAL(10,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    refund_amount       DECIMAL(10,2),
    refund_reason       TEXT,
    refunded_at         DATETIME,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- audit_log
CREATE TABLE audit_log (
    id              CHAR(36) PRIMARY KEY,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       CHAR(36) NOT NULL,
    action          VARCHAR(100) NOT NULL,
    performed_by    CHAR(36),
    old_value       JSON,
    new_value       JSON,
    metadata        JSON,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (performed_by) REFERENCES users(id)
);