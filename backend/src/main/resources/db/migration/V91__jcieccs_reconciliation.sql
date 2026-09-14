-- JCIECCS Lifecycle Engine Phase 2: three-way reconciliation (DEMAND vs ACTUAL RECOVERY vs LEDGER
-- POSTING) + exception tracking. Detects discrepancies only - never repairs them. One row per
-- (collection_detail, component) for payroll-run reconciliation, refreshed (never duplicated) on repeat
-- runs via the partial unique index below; loan-level/integrity checks (reconcileLoan,
-- JciEccsIntegrityCheckService) are computed on-demand and are NOT persisted here.
CREATE TABLE IF NOT EXISTS jcieccs_reconciliation (
    id                    BIGSERIAL PRIMARY KEY,
    payroll_run_id        VARCHAR(50) NULL,
    collection_batch_id   BIGINT      NULL REFERENCES jcieccs_collection_batch (id),
    collection_detail_id  BIGINT      NULL REFERENCES jcieccs_collection_detail (id),
    member_id             BIGINT      NOT NULL REFERENCES jcieccs_member (id),
    employee_id           BIGINT      NOT NULL,
    loan_id               BIGINT      NULL REFERENCES jcieccs_loan (id),
    component             VARCHAR(30) NULL,
    expected_amount       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    actual_amount         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    posted_amount         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    variance_amount       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status                VARCHAR(30) NOT NULL,
    reason_code           VARCHAR(40) NOT NULL,
    detected_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved              BOOLEAN     NOT NULL DEFAULT FALSE,
    resolved_at           TIMESTAMPTZ NULL,
    resolved_by           BIGINT      NULL,
    resolution_action     VARCHAR(40) NULL,
    resolution_remarks    VARCHAR(500) NULL,
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_jcieccs_recon_status CHECK (status IN
        ('MATCHED', 'PARTIAL', 'NOT_RECOVERED', 'OVER_RECOVERED', 'POSTING_PENDING', 'POSTING_MISMATCH',
         'REVERSED', 'LOCKED_SNAPSHOT_CONFLICT', 'ERROR')),
    CONSTRAINT chk_jcieccs_recon_reason CHECK (reason_code IN
        ('PARTIAL_PAYROLL_DEBIT', 'FAILED_PAYROLL_DEBIT', 'OVER_DEBIT', 'POSTING_MISMATCH', 'DUPLICATE_CONFIRMATION',
         'CASH_RECOVERY_AFTER_SNAPSHOT_LOCK', 'REVERSAL_AFTER_PAYROLL_POSTING', 'LOAN_BALANCE_MISMATCH',
         'SCHEDULE_RECOVERY_MISMATCH', 'UNKNOWN')),
    CONSTRAINT chk_jcieccs_recon_component CHECK (component IS NULL OR component IN
        ('THRIFT', 'TERM_INTEREST', 'TERM_PRINCIPAL', 'EMERGENCY_INTEREST', 'EMERGENCY_PRINCIPAL')),
    CONSTRAINT chk_jcieccs_recon_resolution_action CHECK (resolution_action IS NULL OR resolution_action IN
        ('ACKNOWLEDGE', 'MARK_RESOLVED', 'REQUEST_PAYROLL_CORRECTION', 'REVERSE_RECOVERY', 'NO_ACTION_REQUIRED')),
    CONSTRAINT chk_jcieccs_recon_resolution_consistency
        CHECK ((resolved = FALSE AND resolved_at IS NULL AND resolved_by IS NULL)
            OR (resolved = TRUE AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND resolution_action IS NOT NULL))
);

-- Repeatable runs upsert by (collection_detail, component) rather than duplicating rows.
CREATE UNIQUE INDEX IF NOT EXISTS uq_jcieccs_recon_detail_component
    ON jcieccs_reconciliation (collection_detail_id, component) WHERE collection_detail_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_jcieccs_recon_batch ON jcieccs_reconciliation (collection_batch_id);
CREATE INDEX IF NOT EXISTS idx_jcieccs_recon_member ON jcieccs_reconciliation (member_id);
CREATE INDEX IF NOT EXISTS idx_jcieccs_recon_status ON jcieccs_reconciliation (status) WHERE resolved = FALSE;
