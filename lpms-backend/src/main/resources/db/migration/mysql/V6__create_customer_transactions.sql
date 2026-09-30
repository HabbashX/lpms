-- V6: immutable customer financial ledger
CREATE TABLE customer_transactions
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT        NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    amount         DECIMAL(19,4) NOT NULL,
    direction      VARCHAR(10)   NOT NULL,
    balance_after  DECIMAL(19,4) NOT NULL,
    reference_type VARCHAR(20)   NULL,
    reference_id   BIGINT        NULL,
    description    VARCHAR(255)  NULL,
    created_by     BIGINT        NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_customer_transactions_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_customer_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_customer_transactions_type
        CHECK (type IN ('SALE', 'PAYMENT', 'ADJUSTMENT', 'REFUND')),
    CONSTRAINT ck_customer_transactions_direction CHECK (direction IN ('DEBIT', 'CREDIT'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_customer_tx_customer_created ON customer_transactions (customer_id, created_at);
CREATE INDEX idx_customer_tx_reference ON customer_transactions (reference_type, reference_id);
CREATE INDEX idx_customer_tx_type ON customer_transactions (type);
