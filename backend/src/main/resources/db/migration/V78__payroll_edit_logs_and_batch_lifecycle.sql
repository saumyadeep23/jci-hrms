-- Payroll Batch reconciliation/edit support:
--   1. payroll_edit_logs - audit trail for manual line-item edits made against a DRAFT/CALCULATED
--      payroll_monthly_records row (see PayrollBatchEditService).
--   2. payroll_batches.batch_type/pay_date - the batch-create endpoint (POST /api/v1/payroll/batches,
--      previously nonexistent - a row had to already exist before /calculate could be called) accepts
--      these; not part of the original live shape (see V69's own comment on this table).
--   3. payroll_batches.status gains CALCULATED - processBatch()/PayrollBatchComputationService
--      previously left status untouched (still DRAFT) after a successful compute, so there was no way
--      to tell "not yet computed" apart from "computed, awaiting HR finalize" from the batch row alone.
--      DRAFT -> CALCULATED (processBatch) -> HR_FINALIZED (finalizeBatch) -> DISBURSED (CpfLedgerSyncService)
--      is now the real progression; edits are only permitted while DRAFT or CALCULATED.

CREATE TABLE IF NOT EXISTS payroll_edit_logs (
    id             BIGSERIAL     PRIMARY KEY,
    batch_id       BIGINT        NOT NULL REFERENCES payroll_batches (id) ON DELETE CASCADE,
    tran_id        BIGINT        NOT NULL REFERENCES payroll_monthly_records (tran_id) ON DELETE CASCADE,
    employee_id    BIGINT        NOT NULL REFERENCES employees (id),
    head_count     INT           NOT NULL REFERENCES payroll_salary_heads (head_count),
    old_amount     NUMERIC(12,2) NOT NULL,
    new_amount     NUMERIC(12,2) NOT NULL,
    change_reason  VARCHAR(500)  NOT NULL,
    edited_by      BIGINT        REFERENCES employees (id),
    edited_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_payroll_edit_logs_tran ON payroll_edit_logs (tran_id);
CREATE INDEX IF NOT EXISTS idx_payroll_edit_logs_batch ON payroll_edit_logs (batch_id);

ALTER TABLE payroll_batches ADD COLUMN IF NOT EXISTS batch_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR';
DO $$ BEGIN
    ALTER TABLE payroll_batches
        ADD CONSTRAINT chk_payroll_batch_type CHECK (batch_type IN ('REGULAR', 'SUPPLEMENTARY'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
ALTER TABLE payroll_batches ADD COLUMN IF NOT EXISTS pay_date DATE;

ALTER TABLE payroll_batches DROP CONSTRAINT IF EXISTS payroll_batches_status_check;
ALTER TABLE payroll_batches ADD CONSTRAINT payroll_batches_status_check
    CHECK (status IN ('DRAFT', 'CALCULATED', 'HR_FINALIZED', 'FINANCE_APPROVED', 'REJECTED_TO_HR', 'DISBURSED', 'CANCELLED'));
