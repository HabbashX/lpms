-- V5: sales, sale items (historical snapshots) and refund reversals
CREATE TABLE sales
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT        NULL,
    created_by     BIGINT        NULL,
    payment_method VARCHAR(20)   NOT NULL,
    subtotal       DECIMAL(19,4) NOT NULL,
    discount       DECIMAL(19,4) NOT NULL,
    total          DECIMAL(19,4) NOT NULL,
    amount_paid    DECIMAL(19,4) NOT NULL,
    amount_due     DECIMAL(19,4) NOT NULL,
    cost_total     DECIMAL(19,4) NOT NULL,
    profit_total   DECIMAL(19,4) NOT NULL,
    refunded_total DECIMAL(19,4) NOT NULL DEFAULT 0,
    refunded_cost  DECIMAL(19,4) NOT NULL DEFAULT 0,
    status         VARCHAR(30)   NOT NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_sales_status CHECK (status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    CONSTRAINT ck_sales_payment_method
        CHECK (payment_method IN ('CASH', 'CARD', 'CREDIT', 'BANK_TRANSFER', 'INSURANCE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_sales_created_at ON sales (created_at);
CREATE INDEX idx_sales_customer ON sales (customer_id, created_at);
CREATE INDEX idx_sales_created_by ON sales (created_by);
CREATE INDEX idx_sales_payment_method ON sales (payment_method);
CREATE INDEX idx_sales_status ON sales (status);

CREATE TABLE sale_items
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    sale_id            BIGINT        NOT NULL,
    drug_id            BIGINT        NOT NULL,
    drug_name          VARCHAR(150)  NOT NULL,
    quantity           INT           NOT NULL,
    unit_selling_price DECIMAL(19,4) NOT NULL,
    unit_cost_price    DECIMAL(19,4) NOT NULL,
    discount_amount    DECIMAL(19,4) NOT NULL DEFAULT 0,
    line_total         DECIMAL(19,4) NOT NULL,
    revenue            DECIMAL(19,4) NOT NULL,
    cost_total         DECIMAL(19,4) NOT NULL,
    profit             DECIMAL(19,4) NOT NULL,
    refunded_quantity  INT           NOT NULL DEFAULT 0,
    refunded_amount    DECIMAL(19,4) NOT NULL DEFAULT 0,
    refunded_cost      DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sale_items_drug FOREIGN KEY (drug_id) REFERENCES drugs (id),
    CONSTRAINT ck_sale_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sale_items_refunded CHECK (refunded_quantity >= 0 AND refunded_quantity <= quantity)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);
CREATE INDEX idx_sale_items_drug ON sale_items (drug_id);

CREATE TABLE refunds
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    sale_id      BIGINT        NOT NULL,
    customer_id  BIGINT        NULL,
    total_amount DECIMAL(19,4) NOT NULL,
    total_cost   DECIMAL(19,4) NOT NULL,
    total_profit DECIMAL(19,4) NOT NULL,
    reason       VARCHAR(255)  NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_refunds_sale FOREIGN KEY (sale_id) REFERENCES sales (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_refunds_sale ON refunds (sale_id);
CREATE INDEX idx_refunds_created_at ON refunds (created_at);

CREATE TABLE refund_items
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    refund_id          BIGINT        NOT NULL,
    sale_item_id       BIGINT        NOT NULL,
    quantity           INT           NOT NULL,
    unit_selling_price DECIMAL(19,4) NOT NULL,
    unit_cost_price    DECIMAL(19,4) NOT NULL,
    amount             DECIMAL(19,4) NOT NULL,
    cost               DECIMAL(19,4) NOT NULL,
    profit             DECIMAL(19,4) NOT NULL,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_refund_items_refund FOREIGN KEY (refund_id) REFERENCES refunds (id),
    CONSTRAINT fk_refund_items_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_items (id),
    CONSTRAINT ck_refund_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_refund_items_refund ON refund_items (refund_id);
CREATE INDEX idx_refund_items_sale_item ON refund_items (sale_item_id);
