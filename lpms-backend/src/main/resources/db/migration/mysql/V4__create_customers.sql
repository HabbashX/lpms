-- V4: customers, their account aggregates and recorded payments
CREATE TABLE customers
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    phone      VARCHAR(30)  NULL,
    address    VARCHAR(255) NULL,
    notes      VARCHAR(500) NULL,
    active     BIT(1)       NOT NULL DEFAULT b'1',
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_customers_phone ON customers (phone);
CREATE INDEX idx_customers_name ON customers (name);

CREATE TABLE customer_accounts
(
    customer_id     BIGINT        NOT NULL,
    total_purchases DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_paid      DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_refunds   DECIMAL(19,4) NOT NULL DEFAULT 0,
    balance         DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (customer_id),
    CONSTRAINT fk_customer_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE customer_payments
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT        NOT NULL,
    amount         DECIMAL(19,4) NOT NULL,
    payment_method VARCHAR(20)   NOT NULL,
    notes          VARCHAR(255)  NULL,
    created_by     BIGINT        NULL,
    transaction_id BIGINT        NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_customer_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_customer_payments_amount CHECK (amount > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_customer_payments_customer ON customer_payments (customer_id);
CREATE INDEX idx_customer_payments_created ON customer_payments (created_at);
