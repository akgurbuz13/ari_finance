-- V013: Fix blockchain transaction constraints and add metadata column
-- Aligns database constraints with actual code operations

-- 1. Add metadata column for storing transfer tracking information (JSON)
ALTER TABLE blockchain.transactions
    ADD COLUMN IF NOT EXISTS metadata JSONB;

-- 2. Create index on metadata for transfer ID lookups
CREATE INDEX IF NOT EXISTS idx_bc_tx_metadata_transfer
    ON blockchain.transactions USING gin (metadata jsonb_path_ops);

-- 3. Drop old operation constraint (uses bridge_send/bridge_receive)
ALTER TABLE blockchain.transactions
    DROP CONSTRAINT IF EXISTS chk_bc_tx_operation;

-- 4. Add new operation constraint with correct bridge operations
-- bridge_initiate: Initiating a cross-chain transfer (locking tokens on source)
-- bridge_back: Bridging wrapped tokens back to home chain (burning)
-- bridge_complete: Completing a bridge transfer (minting on destination)
ALTER TABLE blockchain.transactions
    ADD CONSTRAINT chk_bc_tx_operation
    CHECK (operation IN (
        'mint',
        'burn',
        'transfer',
        'bridge_initiate',
        'bridge_back',
        'bridge_complete',
        'relay'
    ));

-- 5. Drop old status constraint
ALTER TABLE blockchain.transactions
    DROP CONSTRAINT IF EXISTS chk_bc_tx_status;

-- 6. Add new status constraint with pending_relay for bridge operations
ALTER TABLE blockchain.transactions
    ADD CONSTRAINT chk_bc_tx_status
    CHECK (status IN (
        'pending',           -- Initial state
        'submitted',         -- Transaction submitted to chain
        'pending_relay',     -- Waiting for ICM relay (bridge transfers)
        'confirmed',         -- Transaction confirmed on-chain
        'failed'             -- Transaction failed
    ));

-- 7. Add index for pending bridge transfers (used by findPendingBridgeTransfers)
CREATE INDEX IF NOT EXISTS idx_bc_tx_pending_bridge
    ON blockchain.transactions(chain_id, operation, status)
    WHERE operation IN ('bridge_initiate', 'bridge_back')
      AND status IN ('pending', 'submitted', 'pending_relay');
