-- The unified Payroll Computation Engine (PayrollBatchComputationService) needs to tag a Head 20
-- (Leave Encashment) application against the payroll_batches run that queued it, and release it again
-- on re-run - the same lifecycle leave_encashment_application.payroll_run_id already has, but that
-- column is FK'd to payroll_runs (the older, still-active PayrollRunService/cycle-based engine - see
-- V64). Reusing it here would either violate that FK (a payroll_batches id is not a payroll_runs id)
-- or silently corrupt the older engine's own bookkeeping, so this adds a separate, batch-scoped column
-- instead of repurposing payroll_run_id.
ALTER TABLE leave_encashment_application
    ADD COLUMN payroll_batch_id BIGINT REFERENCES payroll_batches (id);

CREATE INDEX ix_leave_encashment_application_payroll_batch_id ON leave_encashment_application (payroll_batch_id);
