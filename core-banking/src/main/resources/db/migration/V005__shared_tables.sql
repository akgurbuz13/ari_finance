-- V005: Shared tables (outbox, audit log)

CREATE TABLE shared.outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  TEXT NOT NULL,
    aggregate_id    TEXT NOT NULL,
    event_type      TEXT NOT NULL,
    payload         JSONB NOT NULL,
    published       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_unpublished ON shared.outbox_events(published, created_at) WHERE NOT published;

CREATE TABLE shared.audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        UUID,
    actor_type      TEXT NOT NULL,
    action          TEXT NOT NULL,
    resource_type   TEXT NOT NULL,
    resource_id     TEXT NOT NULL,
    details         JSONB,
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_actor_type CHECK (actor_type IN ('user', 'admin', 'system'))
);

CREATE INDEX idx_audit_log_actor ON shared.audit_log(actor_id);
CREATE INDEX idx_audit_log_resource ON shared.audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_created ON shared.audit_log(created_at);

-- System accounts for float, fees, and safeguarding
-- These are created per currency and used for internal bookkeeping
CREATE TABLE ledger.system_accounts_initialized (
    id              BOOLEAN PRIMARY KEY DEFAULT true,
    initialized_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT single_row CHECK (id = true)
);
