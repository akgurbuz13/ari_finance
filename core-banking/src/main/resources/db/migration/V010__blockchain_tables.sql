-- V010: Phase 5 - Blockchain service tables
-- Custodial wallets, on-chain transactions, chain events, reconciliation

CREATE SCHEMA IF NOT EXISTS blockchain;

-- 1. Custodial wallets: maps users to on-chain addresses per chain
CREATE TABLE blockchain.custodial_wallets (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             UUID NOT NULL,
    chain_id            BIGINT NOT NULL,
    address             VARCHAR(42) NOT NULL,
    derivation_index    INT NOT NULL,
    encrypted_key_ref   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, chain_id),
    UNIQUE(chain_id, address)
);
CREATE INDEX idx_wallets_user ON blockchain.custodial_wallets(user_id);
CREATE INDEX idx_wallets_address ON blockchain.custodial_wallets(address);

-- 2. Blockchain transactions: tracks all on-chain operations
CREATE TABLE blockchain.transactions (
    id                  BIGSERIAL PRIMARY KEY,
    tx_hash             VARCHAR(66) NOT NULL,
    chain_id            BIGINT NOT NULL,
    operation           TEXT NOT NULL,
    from_address        VARCHAR(42),
    to_address          VARCHAR(42),
    amount              NUMERIC(38, 18) NOT NULL DEFAULT 0,
    currency            TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'pending',
    block_number        BIGINT,
    gas_used            BIGINT,
    payment_order_id    UUID,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at        TIMESTAMPTZ,
    CONSTRAINT chk_bc_tx_operation CHECK (operation IN ('mint', 'burn', 'transfer', 'bridge_send', 'bridge_receive', 'relay')),
    CONSTRAINT chk_bc_tx_status CHECK (status IN ('pending', 'submitted', 'confirmed', 'failed'))
);
CREATE UNIQUE INDEX idx_bc_tx_hash ON blockchain.transactions(chain_id, tx_hash);
CREATE INDEX idx_bc_tx_payment ON blockchain.transactions(payment_order_id) WHERE payment_order_id IS NOT NULL;
CREATE INDEX idx_bc_tx_status ON blockchain.transactions(status) WHERE status IN ('pending', 'submitted');

-- 3. Chain event tracking: persists processed on-chain events
CREATE TABLE blockchain.chain_events (
    id                  BIGSERIAL PRIMARY KEY,
    chain_id            BIGINT NOT NULL,
    block_number        BIGINT NOT NULL,
    tx_hash             VARCHAR(66) NOT NULL,
    log_index           INT NOT NULL,
    event_type          TEXT NOT NULL,
    contract_address    VARCHAR(42) NOT NULL,
    from_address        VARCHAR(42),
    to_address          VARCHAR(42),
    amount              NUMERIC(38, 18),
    raw_data            JSONB,
    processed           BOOLEAN NOT NULL DEFAULT false,
    processed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(chain_id, tx_hash, log_index)
);
CREATE INDEX idx_chain_events_block ON blockchain.chain_events(chain_id, block_number);
CREATE INDEX idx_chain_events_unprocessed ON blockchain.chain_events(chain_id) WHERE NOT processed;

-- 4. Block cursor: tracks last processed block per chain
CREATE TABLE blockchain.block_cursors (
    chain_id            BIGINT PRIMARY KEY,
    last_block          BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. On-chain reconciliation results
CREATE TABLE blockchain.reconciliations (
    id                  BIGSERIAL PRIMARY KEY,
    chain_id            BIGINT NOT NULL,
    currency            TEXT NOT NULL,
    on_chain_supply     NUMERIC(38, 18) NOT NULL DEFAULT 0,
    off_chain_total     NUMERIC(38, 18) NOT NULL DEFAULT 0,
    difference          NUMERIC(38, 18) NOT NULL DEFAULT 0,
    matched             BOOLEAN NOT NULL DEFAULT false,
    reconciliation_date DATE NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(chain_id, currency, reconciliation_date)
);
