-- V009: Phase 4 - Payment Rail Integration
-- Adds IBAN support, rail reference mapping, reconciliation, and safeguarding tracking

-- 1. Add IBAN column to accounts
ALTER TABLE ledger.accounts ADD COLUMN iban TEXT UNIQUE;
CREATE INDEX idx_accounts_iban ON ledger.accounts(iban) WHERE iban IS NOT NULL;

-- 2. Rail references: maps external rail reference IDs to internal payment orders
CREATE TABLE payments.rail_references (
    id                  BIGSERIAL PRIMARY KEY,
    payment_order_id    UUID NOT NULL REFERENCES payments.payment_orders(id),
    provider            TEXT NOT NULL,
    external_reference  TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'pending',
    raw_response        JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rail_ref_provider CHECK (provider IN ('fast', 'eft', 'sepa', 'sepa_instant')),
    CONSTRAINT chk_rail_ref_status CHECK (status IN ('pending', 'submitted', 'confirmed', 'failed', 'reversed'))
);
CREATE UNIQUE INDEX idx_rail_references_external ON payments.rail_references(provider, external_reference);
CREATE INDEX idx_rail_references_payment ON payments.rail_references(payment_order_id);
CREATE INDEX idx_rail_references_status ON payments.rail_references(status) WHERE status IN ('pending', 'submitted');

-- 3. Reconciliation records: daily rail settlement vs ledger comparison
CREATE TABLE payments.rail_reconciliations (
    id                  BIGSERIAL PRIMARY KEY,
    rail_provider       TEXT NOT NULL,
    reconciliation_date DATE NOT NULL,
    expected_amount     NUMERIC(20, 8) NOT NULL,
    actual_amount       NUMERIC(20, 8) NOT NULL,
    currency            TEXT NOT NULL,
    discrepancy         NUMERIC(20, 8) NOT NULL DEFAULT 0,
    matched_count       INT NOT NULL DEFAULT 0,
    unmatched_count     INT NOT NULL DEFAULT 0,
    status              TEXT NOT NULL DEFAULT 'pending',
    notes               TEXT,
    resolved_by         UUID,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(rail_provider, reconciliation_date, currency),
    CONSTRAINT chk_recon_status CHECK (status IN ('pending', 'matched', 'discrepancy', 'resolved'))
);

-- 4. Safeguarding balance tracking: regulatory requirement for fund segregation
CREATE TABLE payments.safeguarding_balances (
    id              BIGSERIAL PRIMARY KEY,
    currency        TEXT NOT NULL,
    region          TEXT NOT NULL,
    bank_name       TEXT NOT NULL,
    bank_account    TEXT NOT NULL,
    ledger_balance  NUMERIC(20, 8) NOT NULL,
    bank_balance    NUMERIC(20, 8) NOT NULL,
    discrepancy     NUMERIC(20, 8) NOT NULL DEFAULT 0,
    as_of_date      DATE NOT NULL,
    reconciled      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(currency, region, as_of_date),
    CONSTRAINT chk_safeguard_currency CHECK (currency IN ('TRY', 'EUR')),
    CONSTRAINT chk_safeguard_region CHECK (region IN ('TR', 'EU'))
);

-- 5. Webhook deduplication: prevent double-processing of rail callbacks
CREATE TABLE payments.webhook_events (
    id                  BIGSERIAL PRIMARY KEY,
    provider            TEXT NOT NULL,
    event_id            TEXT NOT NULL,
    event_type          TEXT NOT NULL,
    payload             JSONB NOT NULL,
    processed           BOOLEAN NOT NULL DEFAULT false,
    processed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider, event_id)
);
CREATE INDEX idx_webhook_events_unprocessed ON payments.webhook_events(provider, created_at) WHERE NOT processed;
