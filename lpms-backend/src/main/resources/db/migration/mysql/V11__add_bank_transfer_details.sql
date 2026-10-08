-- Bank transfer destination details, captured when a sale is paid by BANK_TRANSFER.
--
-- Nullable: a CASH/CARD/CREDIT/INSURANCE sale has no destination, and the columns are
-- nullable so existing rows need no backfill.
--
-- The identifier is deliberately a single column rather than one per provider. PayPal
-- is identified by an email, Bank of Palestine by an account number, and Jawwal Pay by a
-- phone number - one VARCHAR with a provider-specific label covers all three without
-- widening the table for every new wallet.

ALTER TABLE sales
  ADD COLUMN transfer_provider VARCHAR(30) NULL COMMENT 'PAYPAL | BANK_OF_PALESTINE | JAWWAL_PAY' AFTER payment_method,
  ADD COLUMN transfer_account_name VARCHAR(120) NULL COMMENT 'Name on the receiving account' AFTER transfer_provider,
  ADD COLUMN transfer_account_identifier VARCHAR(120) NULL COMMENT 'PayPal email, BoP account number, or Jawwal phone' AFTER transfer_account_name;

-- Keep in step with com.larv.pharmacy.common.domain.TransferProvider.
ALTER TABLE sales
  ADD CONSTRAINT ck_sales_transfer_provider
  CHECK (transfer_provider IS NULL
         OR transfer_provider IN ('PAYPAL', 'BANK_OF_PALESTINE', 'JAWWAL_PAY'));

-- Reporting: "which destination did we receive the most into?" reads by provider.
CREATE INDEX idx_sales_transfer_provider ON sales (transfer_provider, created_at);

-- A recorded transfer must be complete. Either all three columns are present or none are.
-- MySQL 8 evaluates CHECK constraints on existing rows, so this also proves the migration
-- cannot half-populate a row on a table that already holds data.
ALTER TABLE sales
  ADD CONSTRAINT ck_sales_transfer_complete
  CHECK ((transfer_provider IS NULL
          AND transfer_account_name IS NULL
          AND transfer_account_identifier IS NULL)
         OR (transfer_provider IS NOT NULL
             AND transfer_account_name IS NOT NULL
             AND transfer_account_identifier IS NOT NULL));
