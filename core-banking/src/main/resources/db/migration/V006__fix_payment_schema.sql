-- V006: Add missing columns to payment_orders and fix payment_status_history

-- Add missing columns to payment_orders
ALTER TABLE payments.payment_orders
    ADD COLUMN IF NOT EXISTS fee_amount NUMERIC(20, 8) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fee_currency TEXT,
    ADD COLUMN IF NOT EXISTS ledger_transaction_id UUID,
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

-- Fix payment_status_history: rename status to from_status/to_status
ALTER TABLE payments.payment_status_history
    RENAME COLUMN status TO to_status;

ALTER TABLE payments.payment_status_history
    ADD COLUMN IF NOT EXISTS from_status TEXT;

-- Add index for ledger transaction lookups
CREATE INDEX IF NOT EXISTS idx_payment_orders_ledger_tx
    ON payments.payment_orders(ledger_transaction_id)
    WHERE ledger_transaction_id IS NOT NULL;
