-- V012: Bank statement integration tables for safeguarding reconciliation

-- ============================================================
-- Bank Account Balance Snapshots
-- ============================================================

CREATE TABLE payments.bank_account_balances (
    id                  UUID PRIMARY KEY,
    bank_account_id     VARCHAR(100) NOT NULL,  -- IBAN or internal account ID
    currency            VARCHAR(3) NOT NULL,
    available_balance   NUMERIC(20, 8) NOT NULL,
    current_balance     NUMERIC(20, 8) NOT NULL,
    pending_credits     NUMERIC(20, 8) DEFAULT 0,
    pending_debits      NUMERIC(20, 8) DEFAULT 0,
    as_of_timestamp     TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bank_balances_account ON payments.bank_account_balances(bank_account_id);
CREATE INDEX idx_bank_balances_timestamp ON payments.bank_account_balances(as_of_timestamp DESC);

COMMENT ON TABLE payments.bank_account_balances IS 'Point-in-time balance snapshots from bank statements';

-- ============================================================
-- Bank Statements (MT940 or camt.053)
-- ============================================================

CREATE TABLE payments.bank_statements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_account_id     VARCHAR(100) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    opening_balance     NUMERIC(20, 8) NOT NULL,
    closing_balance     NUMERIC(20, 8) NOT NULL,
    total_credits       NUMERIC(20, 8) NOT NULL,
    total_debits        NUMERIC(20, 8) NOT NULL,
    transaction_count   INT NOT NULL,
    from_date           DATE NOT NULL,
    to_date             DATE NOT NULL,
    statement_id        VARCHAR(100) NOT NULL,  -- Bank's statement reference
    raw_content         TEXT,                    -- Original MT940/camt.053 content
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,

    CONSTRAINT uq_bank_statement UNIQUE (bank_account_id, statement_id)
);

CREATE INDEX idx_bank_statements_account ON payments.bank_statements(bank_account_id);
CREATE INDEX idx_bank_statements_dates ON payments.bank_statements(from_date, to_date);

COMMENT ON TABLE payments.bank_statements IS 'Imported bank statements for reconciliation';

-- ============================================================
-- Bank Transactions (from statements)
-- ============================================================

CREATE TABLE payments.bank_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      VARCHAR(100) NOT NULL,  -- Bank's transaction reference
    bank_account_id     VARCHAR(100) NOT NULL,
    type                VARCHAR(10) NOT NULL,   -- CREDIT, DEBIT
    amount              NUMERIC(20, 8) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    value_date          DATE NOT NULL,
    booking_date        DATE NOT NULL,
    reference           VARCHAR(200),           -- Payment reference/description
    counterparty_name   VARCHAR(200),
    counterparty_iban   VARCHAR(50),
    description         TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    matched_ledger_id   UUID,                   -- Linked ledger entry when reconciled
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bank_tx_type CHECK (type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_bank_tx_status CHECK (status IN ('BOOKED', 'PENDING', 'REVERSED')),
    CONSTRAINT uq_bank_transaction UNIQUE (bank_account_id, transaction_id)
);

CREATE INDEX idx_bank_transactions_account ON payments.bank_transactions(bank_account_id);
CREATE INDEX idx_bank_transactions_date ON payments.bank_transactions(booking_date);
CREATE INDEX idx_bank_transactions_unmatched ON payments.bank_transactions(bank_account_id)
    WHERE matched_ledger_id IS NULL;

COMMENT ON TABLE payments.bank_transactions IS 'Individual transactions from bank statements';

-- ============================================================
-- Safeguarding Bank Accounts Registry
-- ============================================================

CREATE TABLE payments.safeguarding_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region              VARCHAR(10) NOT NULL,   -- TR, EU
    currency            VARCHAR(3) NOT NULL,
    bank_name           VARCHAR(200) NOT NULL,
    bank_bic            VARCHAR(11),
    account_iban        VARCHAR(50) NOT NULL,
    account_number      VARCHAR(50),
    internal_account_id UUID REFERENCES ledger.accounts(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,

    CONSTRAINT chk_safeguarding_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT uq_safeguarding_account UNIQUE (region, currency, account_iban)
);

COMMENT ON TABLE payments.safeguarding_accounts IS 'Registry of safeguarding bank accounts per region';

-- ============================================================
-- Add notes column to safeguarding_balances if not exists
-- ============================================================

ALTER TABLE payments.safeguarding_balances
ADD COLUMN IF NOT EXISTS notes TEXT;

-- ============================================================
-- Statement import audit log
-- ============================================================

CREATE TABLE payments.statement_imports (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_account_id     VARCHAR(100) NOT NULL,
    source_type         VARCHAR(20) NOT NULL,   -- SFTP, API, MANUAL
    file_name           VARCHAR(200),
    statement_id        VARCHAR(100),
    transaction_count   INT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message       TEXT,
    imported_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_import_source CHECK (source_type IN ('SFTP', 'API', 'MANUAL')),
    CONSTRAINT chk_import_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_statement_imports_account ON payments.statement_imports(bank_account_id);
CREATE INDEX idx_statement_imports_status ON payments.statement_imports(status);

COMMENT ON TABLE payments.statement_imports IS 'Audit log of bank statement imports';
