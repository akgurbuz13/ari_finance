-- V003: Ledger module tables (double-entry accounting)

CREATE TABLE ledger.accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.users(id),
    currency        TEXT NOT NULL,
    account_type    TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, currency, account_type),
    CONSTRAINT chk_currency CHECK (currency IN ('TRY', 'EUR')),
    CONSTRAINT chk_account_type CHECK (account_type IN ('user_wallet', 'system_float', 'fee_revenue', 'safeguarding')),
    CONSTRAINT chk_account_status CHECK (status IN ('active', 'frozen', 'closed'))
);

CREATE TABLE ledger.transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key TEXT NOT NULL UNIQUE,
    type            TEXT NOT NULL,
    status          TEXT NOT NULL,
    reference_id    TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT chk_tx_type CHECK (type IN ('deposit', 'withdrawal', 'p2p_transfer', 'fx_conversion', 'cross_border', 'mint', 'burn', 'fee')),
    CONSTRAINT chk_tx_status CHECK (status IN ('pending', 'completed', 'failed', 'reversed'))
);

CREATE TABLE ledger.entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  UUID NOT NULL REFERENCES ledger.transactions(id),
    account_id      UUID NOT NULL REFERENCES ledger.accounts(id),
    direction       TEXT NOT NULL CHECK (direction IN ('debit', 'credit')),
    amount          NUMERIC(20, 8) NOT NULL CHECK (amount > 0),
    currency        TEXT NOT NULL,
    balance_after   NUMERIC(20, 8) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_entries_account_created ON ledger.entries(account_id, created_at DESC);
CREATE INDEX idx_entries_transaction ON ledger.entries(transaction_id);
CREATE INDEX idx_transactions_idempotency ON ledger.transactions(idempotency_key);
CREATE INDEX idx_transactions_status ON ledger.transactions(status);
CREATE INDEX idx_transactions_type ON ledger.transactions(type);
CREATE INDEX idx_accounts_user ON ledger.accounts(user_id);
