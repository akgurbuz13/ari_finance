-- V007: Compliance cases table for case management

CREATE TABLE shared.compliance_cases (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES identity.users(id),
    type            TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'open',
    description     TEXT NOT NULL,
    assigned_to     UUID,
    resolution      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    CONSTRAINT chk_case_status CHECK (status IN ('open', 'under_review', 'resolved', 'dismissed'))
);

CREATE INDEX idx_compliance_cases_user ON shared.compliance_cases(user_id);
CREATE INDEX idx_compliance_cases_status ON shared.compliance_cases(status);
