-- V004: Payments module tables

CREATE TABLE payments.payment_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     TEXT NOT NULL UNIQUE,
    type                TEXT NOT NULL,
    status              TEXT NOT NULL,
    sender_account_id   UUID REFERENCES ledger.accounts(id),
    receiver_account_id UUID REFERENCES ledger.accounts(id),
    amount              NUMERIC(20, 8) NOT NULL,
    currency            TEXT NOT NULL,
    fx_quote_id         UUID,
    chain_tx_hash       TEXT,
    rail                TEXT,
    error_code          TEXT,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_type CHECK (type IN ('deposit', 'withdrawal', 'domestic_p2p', 'cross_border')),
    CONSTRAINT chk_payment_status CHECK (status IN ('initiated', 'compliance_check', 'processing', 'settling', 'completed', 'failed', 'reversed')),
    CONSTRAINT chk_payment_rail CHECK (rail IS NULL OR rail IN ('fast', 'eft', 'sepa', 'sepa_instant', 'blockchain', 'internal'))
);

CREATE TABLE payments.payment_status_history (
    id                  BIGSERIAL PRIMARY KEY,
    payment_order_id    UUID NOT NULL REFERENCES payments.payment_orders(id),
    status              TEXT NOT NULL,
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments.fx_quotes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_currency TEXT NOT NULL,
    target_currency TEXT NOT NULL,
    rate            NUMERIC(20, 10) NOT NULL,
    inverse_rate    NUMERIC(20, 10) NOT NULL,
    spread          NUMERIC(10, 6) NOT NULL,
    source_amount   NUMERIC(20, 8),
    target_amount   NUMERIC(20, 8),
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_orders_status ON payments.payment_orders(status);
CREATE INDEX idx_payment_orders_sender ON payments.payment_orders(sender_account_id);
CREATE INDEX idx_payment_orders_receiver ON payments.payment_orders(receiver_account_id);
CREATE INDEX idx_payment_status_history_order ON payments.payment_status_history(payment_order_id);
CREATE INDEX idx_fx_quotes_expires ON payments.fx_quotes(expires_at) WHERE NOT used;
