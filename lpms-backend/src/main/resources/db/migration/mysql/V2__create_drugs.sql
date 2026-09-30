-- V2: drug catalog (categories + drugs). Note: no purchase price on drugs.
CREATE TABLE categories
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE drugs
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(150) NOT NULL,
    generic_name        VARCHAR(150) NULL,
    barcode             VARCHAR(50)  NULL,
    manufacturer        VARCHAR(150) NULL,
    category_id         BIGINT       NULL,
    dosage_form         VARCHAR(30)  NULL,
    strength            VARCHAR(50)  NULL,
    unit                VARCHAR(30)  NULL,
    description         VARCHAR(500) NULL,
    minimum_stock_level INT          NOT NULL DEFAULT 0,
    current_quantity    INT          NOT NULL DEFAULT 0,
    active              BIT(1)       NOT NULL DEFAULT b'1',
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_drugs_barcode UNIQUE (barcode),
    CONSTRAINT fk_drugs_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_drugs_name ON drugs (name);
CREATE INDEX idx_drugs_generic_name ON drugs (generic_name);
CREATE INDEX idx_drugs_category ON drugs (category_id);
CREATE INDEX idx_drugs_active ON drugs (active);
