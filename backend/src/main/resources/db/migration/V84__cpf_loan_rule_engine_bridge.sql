-- Closes the integration gap between the rule-driven cpf_application flow (Task 2) and the
-- pre-existing cpf_loan_applications repayment/recovery/settlement machinery: a refundable
-- cpf_application must create exactly one linked cpf_loan_applications row at disbursement so the
-- already-built payroll recovery / cash settlement / interest phasing actually activates for it.
-- Every table here is additive - no existing column is renamed or dropped, no existing seeded rule
-- data is touched, and cpf_payroll_deduction_cap (V82) is left completely untouched.

-- 1. Source-of-origin link (Part 4/7): at most one loan per originating rule-engine application.
ALTER TABLE cpf_loan_applications
    ADD COLUMN IF NOT EXISTS cpf_application_id UUID NULL REFERENCES cpf_application (id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cpf_loan_applications_cpf_application_id
    ON cpf_loan_applications (cpf_application_id) WHERE cpf_application_id IS NOT NULL;

-- 2. Cash settlement idempotency (Part 23): a given instrument/challan number must settle a given
-- loan at most once. instrument_or_challan_no alone is not globally unique across loans (different
-- members can legitimately bank the same challan series), so the key is scoped per loan.
ALTER TABLE cpf_loan_settlement_transactions
    ADD CONSTRAINT uq_cpf_loan_settlement_loan_instrument UNIQUE (loan_id, instrument_or_challan_no);

-- 3. Loan recovery policy (Parts 15/19): effective-dated, DB-driven repayment-commencement mode and
-- principal-to-interest-installment ratio - same "current row has effective_to IS NULL, revising
-- closes it and inserts a new one" convention as cpf_payroll_deduction_cap (V82), for consistency
-- with this codebase's existing lighter-weight versioning pattern (CpfPayrollDeductionCapService).
CREATE TABLE IF NOT EXISTS cpf_loan_recovery_policy (
    id BIGSERIAL PRIMARY KEY,
    commencement_mode VARCHAR(40) NOT NULL DEFAULT 'NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT'
        CHECK (commencement_mode IN ('NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT')),
    principal_installments_per_interest_installment INT NOT NULL DEFAULT 12
        CHECK (principal_installments_per_interest_installment > 0),
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    remarks TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cpf_loan_recovery_policy_current
    ON cpf_loan_recovery_policy (effective_to) WHERE effective_to IS NULL;

INSERT INTO cpf_loan_recovery_policy (commencement_mode, principal_installments_per_interest_installment, effective_from, remarks)
SELECT 'NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT', 12, DATE '2011-04-01',
       'Default JCI CPF Trust loan recovery policy - recovery starts the payroll month after disbursement; one interest instalment recovered for every 12 principal instalments recovered.'
WHERE NOT EXISTS (SELECT 1 FROM cpf_loan_recovery_policy);

-- 4. Payroll-recovery idempotency/audit (Part 24/37): CpfLedgerSyncService.syncForBatch() is
-- explicitly documented as re-runnable against an already-DISBURSED batch; without this table a
-- re-run (or a race against a same-day cash settlement) could post the same principal/interest
-- recovery to the CPF ledger twice. One row per (loan, payroll batch, phase) actually resolved -
-- the unique constraint is the idempotency guard itself, and the row is also the audit trail linking
-- application -> loan -> recovery -> ledger.
CREATE TABLE IF NOT EXISTS cpf_loan_batch_recovery (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES cpf_loan_applications (id),
    payroll_batch_id BIGINT NOT NULL REFERENCES payroll_batches (id),
    recovery_phase VARCHAR(20) NOT NULL,
    amount_recovered NUMERIC(12, 2) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    ledger_entry_id BIGINT NULL REFERENCES cpf_trust_member_ledger_entries (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cpf_loan_batch_recovery UNIQUE (loan_id, payroll_batch_id, recovery_phase)
);

CREATE INDEX IF NOT EXISTS idx_cpf_loan_batch_recovery_loan ON cpf_loan_batch_recovery (loan_id);
