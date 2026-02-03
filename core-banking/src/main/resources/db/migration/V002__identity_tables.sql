-- V002: Identity module tables

CREATE TABLE identity.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL UNIQUE,
    phone           TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    first_name      TEXT,
    last_name       TEXT,
    date_of_birth   DATE,
    nationality     TEXT,
    status          TEXT NOT NULL DEFAULT 'pending_kyc',
    region          TEXT NOT NULL,
    totp_secret     TEXT,
    totp_enabled    BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_status CHECK (status IN ('pending_kyc', 'active', 'suspended', 'closed')),
    CONSTRAINT chk_user_region CHECK (region IN ('TR', 'EU'))
);

CREATE TABLE identity.kyc_verifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.users(id),
    provider        TEXT NOT NULL,
    provider_ref    TEXT NOT NULL,
    status          TEXT NOT NULL,
    level           TEXT NOT NULL,
    decision_by     UUID,
    decision_at     TIMESTAMPTZ,
    rejection_reason TEXT,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_kyc_status CHECK (status IN ('pending', 'approved', 'rejected', 'expired')),
    CONSTRAINT chk_kyc_level CHECK (level IN ('basic', 'enhanced'))
);

CREATE TABLE identity.refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.users(id),
    token_hash      TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON identity.users(email);
CREATE INDEX idx_users_phone ON identity.users(phone);
CREATE INDEX idx_users_status ON identity.users(status);
CREATE INDEX idx_kyc_user_id ON identity.kyc_verifications(user_id);
CREATE INDEX idx_kyc_status ON identity.kyc_verifications(status);
CREATE INDEX idx_refresh_tokens_user ON identity.refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON identity.refresh_tokens(token_hash);
