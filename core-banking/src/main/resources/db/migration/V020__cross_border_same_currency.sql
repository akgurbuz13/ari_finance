-- V020: Support same-currency cross-border transfers
-- Adds region dimension to accounts and cross_border_transit account type

-- 1. Add region to accounts (country-based)
ALTER TABLE ledger.accounts ADD COLUMN IF NOT EXISTS region TEXT;
UPDATE ledger.accounts SET region = CASE
    WHEN currency = 'TRY' THEN 'TR'
    WHEN currency = 'EUR' THEN 'EU'
    ELSE 'TR' END
WHERE region IS NULL;
ALTER TABLE ledger.accounts ALTER COLUMN region SET NOT NULL;

-- 2. Update unique constraint: user can have TRY/TR + TRY/EU
ALTER TABLE ledger.accounts DROP CONSTRAINT IF EXISTS accounts_user_id_currency_account_type_key;
ALTER TABLE ledger.accounts ADD CONSTRAINT accounts_user_id_currency_type_region_key
    UNIQUE(user_id, currency, account_type, region);

-- 3. Add cross_border_transit account type
ALTER TABLE ledger.accounts DROP CONSTRAINT IF EXISTS chk_account_type;
ALTER TABLE ledger.accounts ADD CONSTRAINT chk_account_type
    CHECK (account_type IN ('user_wallet','system_float','fee_revenue','safeguarding','cross_border_transit'));

-- 4. Add chain tracking to payment_orders
ALTER TABLE payments.payment_orders
    ADD COLUMN IF NOT EXISTS source_chain_id BIGINT,
    ADD COLUMN IF NOT EXISTS target_chain_id BIGINT;
