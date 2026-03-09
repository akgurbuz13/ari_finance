-- V022: Add cross_border_same_ccy to payment_orders type constraint
-- This was missed in V020 when same-currency cross-border was added

ALTER TABLE payments.payment_orders DROP CONSTRAINT IF EXISTS chk_payment_type;
ALTER TABLE payments.payment_orders ADD CONSTRAINT chk_payment_type
    CHECK (type IN ('deposit', 'withdrawal', 'domestic_p2p', 'cross_border', 'cross_border_same_ccy'));
