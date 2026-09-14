-- JCIECCS Lifecycle Engine Phase 1: separates RECOVERY (what was actually received) and ALLOCATION
-- (how it was split across components) from LEDGER POSTING (jcieccs_loan_repayment, already-live V87
-- table) and BALANCE UPDATE (jcieccs_loan.outstanding_principal) - previously fused into a single
-- irreversible method (JciEccsDebitConfirmationService.postLoanRepayment /
-- JciEccsLoanService.postCashRepayment). Also introduces reversal (compensating entries, never deletes)
-- and a reconciliation flag for cash repayment landing after a payroll snapshot is already LOCKED.

-- 1. jcieccs_recovery - one row per payroll debit-confirmation line, per cash repayment, or per reversal.
CREATE TABLE IF NOT EXISTS jcieccs_recovery (
    id                              BIGSERIAL PRIMARY KEY,
    member_id                       BIGINT      NOT NULL REFERENCES jcieccs_member (id),
    loan_id                         BIGINT      NULL REFERENCES jcieccs_loan (id),
    source                          VARCHAR(20) NOT NULL,
    status                          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    gross_amount                    NUMERIC(14, 2) NOT NULL,
    payment_date                    DATE        NOT NULL,
    posting_date                    DATE        NULL,
    cycle_id                        BIGINT      NULL REFERENCES hrms_payroll_cycle (id),
    collection_detail_id            BIGINT      NULL REFERENCES jcieccs_collection_detail (id),
    payroll_transaction_reference   VARCHAR(100) NULL,
    receipt_number                  VARCHAR(100) NULL,
    idempotency_key                 VARCHAR(150) NOT NULL,
    reversal_of_recovery_id         BIGINT      NULL REFERENCES jcieccs_recovery (id),
    remarks                         VARCHAR(500) NULL,
    created_by                      BIGINT      NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at                       TIMESTAMPTZ NULL,
    reversed_at                     TIMESTAMPTZ NULL,

    CONSTRAINT jcieccs_recovery_idempotency_key_key UNIQUE (idempotency_key),
    CONSTRAINT chk_jcieccs_recovery_source CHECK (source IN ('PAYROLL', 'CASH', 'REVERSAL')),
    CONSTRAINT chk_jcieccs_recovery_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'PARTIAL', 'FAILED', 'POSTED', 'REVERSED', 'RECONCILIATION_REQUIRED'))
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_recovery_loan ON jcieccs_recovery (loan_id);
CREATE INDEX IF NOT EXISTS idx_jcieccs_recovery_member ON jcieccs_recovery (member_id);

-- 2. jcieccs_recovery_allocation - one row per component a recovery was split across.
CREATE TABLE IF NOT EXISTS jcieccs_recovery_allocation (
    id                    BIGSERIAL PRIMARY KEY,
    recovery_id           BIGINT      NOT NULL REFERENCES jcieccs_recovery (id),
    collection_detail_id  BIGINT      NULL REFERENCES jcieccs_collection_detail (id),
    loan_id               BIGINT      NULL REFERENCES jcieccs_loan (id),
    loan_schedule_id      BIGINT      NULL REFERENCES jcieccs_loan_schedule (id),
    component             VARCHAR(30) NOT NULL,
    expected_amount       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    allocated_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    allocation_sequence   INT         NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_jcieccs_recovery_alloc_component
        CHECK (component IN ('THRIFT', 'TERM_INTEREST', 'TERM_PRINCIPAL', 'EMERGENCY_INTEREST', 'EMERGENCY_PRINCIPAL'))
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_recovery_alloc_recovery ON jcieccs_recovery_allocation (recovery_id);

-- 3. jcieccs_loan_repayment stays the one authoritative ledger table - link it to the recovery/allocation
-- that produced it, and support REVERSAL as a compensating entry (never delete/mutate the original row).
ALTER TABLE jcieccs_loan_repayment ADD COLUMN recovery_id BIGINT NULL REFERENCES jcieccs_recovery (id);
ALTER TABLE jcieccs_loan_repayment ADD COLUMN recovery_allocation_id BIGINT NULL REFERENCES jcieccs_recovery_allocation (id);
ALTER TABLE jcieccs_loan_repayment ADD COLUMN reversal_of_id BIGINT NULL REFERENCES jcieccs_loan_repayment (id);

ALTER TABLE jcieccs_loan_repayment DROP CONSTRAINT chk_jcieccs_rep_source;
ALTER TABLE jcieccs_loan_repayment ADD CONSTRAINT chk_jcieccs_rep_source CHECK (source IN ('PAYROLL', 'CASH', 'ADJUSTMENT', 'REVERSAL'));

-- 4. jcieccs_collection_detail: flag (never silently mutate) when a cash repayment lands on a loan whose
-- current cycle already has a LOCKED, not-yet-debit-confirmed snapshot line - see
-- JciEccsLoanService.postCashRepayment.
ALTER TABLE jcieccs_collection_detail ADD COLUMN reconciliation_reason VARCHAR(60) NULL;

ALTER TABLE jcieccs_collection_detail DROP CONSTRAINT chk_jcieccs_detail_status;
ALTER TABLE jcieccs_collection_detail ADD CONSTRAINT chk_jcieccs_detail_status
    CHECK (debit_status IN ('PENDING_DEBIT', 'DEBIT_SUCCESS', 'DEBIT_PARTIAL', 'DEBIT_FAILED', 'REVERSED', 'RECONCILIATION_REQUIRED'));
