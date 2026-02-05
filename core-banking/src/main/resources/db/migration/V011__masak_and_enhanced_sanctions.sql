-- V011: MASAK reporting tables and enhanced sanctions list

-- ============================================================
-- MASAK Reports Table (Turkey Financial Intelligence Unit)
-- ============================================================

CREATE TABLE shared.masak_reports (
    id                      UUID PRIMARY KEY,
    report_type             VARCHAR(10) NOT NULL,
    subject_user_id         UUID NOT NULL REFERENCES identity.users(id),
    subject_name            TEXT NOT NULL,
    subject_id_number       VARCHAR(50),
    transaction_ids         TEXT[],
    total_amount            NUMERIC(20, 8) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    suspicion_reason        TEXT NOT NULL,
    suspicion_indicators    TEXT[],
    narrative_summary       TEXT NOT NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              UUID,
    approved_at             TIMESTAMPTZ,
    approved_by             UUID,
    submitted_at            TIMESTAMPTZ,
    masak_reference_number  VARCHAR(100),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_report_type CHECK (report_type IN ('SIB', 'TFR', 'ESK', 'MVD')),
    CONSTRAINT chk_report_status CHECK (status IN (
        'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'SUBMITTED',
        'ACKNOWLEDGED', 'UNDER_INVESTIGATION', 'CLOSED'
    ))
);

CREATE INDEX idx_masak_reports_user ON shared.masak_reports(subject_user_id);
CREATE INDEX idx_masak_reports_status ON shared.masak_reports(status);
CREATE INDEX idx_masak_reports_type ON shared.masak_reports(report_type);
CREATE INDEX idx_masak_reports_created ON shared.masak_reports(created_at DESC);

COMMENT ON TABLE shared.masak_reports IS 'MASAK (Mali Suçları Araştırma Kurulu) suspicious transaction reports';
COMMENT ON COLUMN shared.masak_reports.report_type IS 'SIB=Suspicious Transaction, TFR=Terrorist Financing, ESK=Threshold, MVD=Asset Freeze';

-- ============================================================
-- Enhance sanctions_list table
-- ============================================================

-- Add missing columns for full compliance
ALTER TABLE shared.sanctions_list
ADD COLUMN IF NOT EXISTS date_of_birth VARCHAR(20),
ADD COLUMN IF NOT EXISTS national_id VARCHAR(50),
ADD COLUMN IF NOT EXISTS remarks TEXT,
ADD COLUMN IF NOT EXISTS listing_date TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);

-- Update list_type constraint to support more types
ALTER TABLE shared.sanctions_list DROP CONSTRAINT IF EXISTS chk_list_type;
ALTER TABLE shared.sanctions_list ADD CONSTRAINT chk_list_type
    CHECK (list_type IN ('sanctions', 'pep', 'terrorist', 'asset_freeze'));

-- Add unique constraint for external IDs per source
CREATE UNIQUE INDEX IF NOT EXISTS idx_sanctions_list_source_external
    ON shared.sanctions_list(source, external_id)
    WHERE external_id IS NOT NULL;

-- Add index for source-based queries
CREATE INDEX IF NOT EXISTS idx_sanctions_list_source ON shared.sanctions_list(source);

-- ============================================================
-- Threshold monitoring alerts
-- ============================================================

CREATE TABLE shared.threshold_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_type      VARCHAR(30) NOT NULL,
    user_id         UUID NOT NULL REFERENCES identity.users(id),
    amount          NUMERIC(20, 8) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    transaction_id  VARCHAR(50),
    description     TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at     TIMESTAMPTZ,
    reviewed_by     UUID,
    notes           TEXT,

    CONSTRAINT chk_alert_type CHECK (alert_type IN (
        'SINGLE_TRANSACTION', 'DAILY_CUMULATIVE', 'WEEKLY_CUMULATIVE',
        'VELOCITY', 'STRUCTURING', 'GEOGRAPHIC', 'BEHAVIORAL'
    )),
    CONSTRAINT chk_alert_status CHECK (status IN (
        'PENDING', 'UNDER_REVIEW', 'ESCALATED', 'CLEARED', 'REPORTED'
    ))
);

CREATE INDEX idx_threshold_alerts_user ON shared.threshold_alerts(user_id);
CREATE INDEX idx_threshold_alerts_status ON shared.threshold_alerts(status);
CREATE INDEX idx_threshold_alerts_created ON shared.threshold_alerts(created_at DESC);

-- ============================================================
-- Sanctions screening results audit
-- ============================================================

CREATE TABLE shared.screening_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screened_type   VARCHAR(20) NOT NULL,  -- 'user', 'transaction', 'counterparty'
    screened_id     UUID NOT NULL,
    screened_name   TEXT NOT NULL,
    result          VARCHAR(20) NOT NULL,   -- 'clear', 'flagged', 'blocked'
    match_type      VARCHAR(30),
    match_score     DECIMAL(5, 4),
    match_details   TEXT,
    matched_entry_id BIGINT REFERENCES shared.sanctions_list(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address      INET,

    CONSTRAINT chk_screened_type CHECK (screened_type IN ('user', 'transaction', 'counterparty')),
    CONSTRAINT chk_result CHECK (result IN ('clear', 'flagged', 'blocked'))
);

CREATE INDEX idx_screening_results_screened ON shared.screening_results(screened_type, screened_id);
CREATE INDEX idx_screening_results_result ON shared.screening_results(result);
CREATE INDEX idx_screening_results_created ON shared.screening_results(created_at DESC);

-- ============================================================
-- EU/AMLD6 specific: Record retention tracking
-- ============================================================

CREATE TABLE shared.data_retention_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_type       VARCHAR(50) NOT NULL,
    record_id       UUID NOT NULL,
    retention_years INT NOT NULL DEFAULT 5,
    created_at      TIMESTAMPTZ NOT NULL,
    scheduled_purge_at TIMESTAMPTZ NOT NULL,
    purged_at       TIMESTAMPTZ,
    legal_hold      BOOLEAN NOT NULL DEFAULT false,
    hold_reason     TEXT,

    CONSTRAINT chk_retention_years CHECK (retention_years BETWEEN 5 AND 10)
);

CREATE INDEX idx_retention_scheduled ON shared.data_retention_log(scheduled_purge_at)
    WHERE purged_at IS NULL AND legal_hold = false;
CREATE INDEX idx_retention_legal_hold ON shared.data_retention_log(legal_hold)
    WHERE legal_hold = true;

COMMENT ON TABLE shared.data_retention_log IS 'Track data retention for AMLD6/KVKK compliance (5-8 year minimum)';
