-- Add vehicle_escrow_holding to account type constraint
ALTER TABLE ledger.accounts DROP CONSTRAINT IF EXISTS chk_account_type;
ALTER TABLE ledger.accounts ADD CONSTRAINT chk_account_type CHECK (
    account_type = ANY (ARRAY[
        'user_wallet', 'system_float', 'fee_revenue',
        'safeguarding', 'cross_border_transit', 'vehicle_escrow_holding'
    ])
);
