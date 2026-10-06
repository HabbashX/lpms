-- V9: per-drug default selling price (nullable: a drug may never have had one set).
ALTER TABLE drugs
    ADD COLUMN selling_price DECIMAL(19, 4) NULL;

CREATE INDEX idx_drugs_selling_price ON drugs (selling_price);