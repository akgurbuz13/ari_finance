-- Vehicle registrations (postgres cache of on-chain NFT data)
CREATE TABLE payments.vehicle_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id BIGINT,
    owner_user_id UUID NOT NULL REFERENCES identity.users(id),
    vin VARCHAR(17) NOT NULL,
    vin_hash VARCHAR(66) NOT NULL,
    plate_number VARCHAR(20) NOT NULL,
    plate_hash VARCHAR(66) NOT NULL,
    make VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    year INTEGER NOT NULL,
    color VARCHAR(30),
    mileage INTEGER,
    fuel_type VARCHAR(20),
    transmission VARCHAR(20),
    metadata_uri VARCHAR(500),
    chain_id BIGINT NOT NULL,
    mint_tx_hash VARCHAR(66),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(vin_hash),
    UNIQUE(plate_number)
);

-- Vehicle escrows (postgres cache of on-chain escrow state)
CREATE TABLE payments.vehicle_escrows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    on_chain_escrow_id BIGINT,
    vehicle_registration_id UUID NOT NULL REFERENCES payments.vehicle_registrations(id),
    seller_user_id UUID NOT NULL REFERENCES identity.users(id),
    buyer_user_id UUID REFERENCES identity.users(id),
    sale_amount NUMERIC(20,8) NOT NULL,
    fee_amount NUMERIC(20,8) NOT NULL DEFAULT 50.00000000,
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    state VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    seller_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    buyer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    share_code VARCHAR(20) NOT NULL UNIQUE,
    setup_tx_hash VARCHAR(66),
    fund_tx_hash VARCHAR(66),
    complete_tx_hash VARCHAR(66),
    cancel_tx_hash VARCHAR(66),
    payment_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_vehicle_reg_owner ON payments.vehicle_registrations(owner_user_id);
CREATE INDEX idx_vehicle_reg_status ON payments.vehicle_registrations(status);
CREATE INDEX idx_vehicle_escrow_seller ON payments.vehicle_escrows(seller_user_id);
CREATE INDEX idx_vehicle_escrow_buyer ON payments.vehicle_escrows(buyer_user_id);
CREATE INDEX idx_vehicle_escrow_state ON payments.vehicle_escrows(state);
CREATE INDEX idx_vehicle_escrow_share_code ON payments.vehicle_escrows(share_code);
