-- Para 60(2), EPF Scheme: when a financial year's CPF interest rate has not yet been officially notified,
-- the immediately preceding FY's notified rate is applied provisionally. This migration adds the audit
-- trail columns needed to record that on every ledger entry, and a new INTERIM_SETTLEMENT_INTEREST entry
-- type for mid-year exits (superannuation/resignation/death/transfer-out) that crystallize interest before
-- the normal year-end ANNUAL_INTEREST run.
--
-- cpf_statutory_interest_rates (fin_year, base_cpf_rate, loan_markup_rate, effective_loan_rate,
-- effective_from/to, ministry_order_no, order_date, is_active, created_at, updated_at, with a UNIQUE
-- constraint on fin_year) - the notified-rate master this migration's rate resolution reads from - was
-- already created directly against the shared dev database by other tooling before this migration existed
-- (same situation as V74's header comment for cpf_trust_member_ledger_entries etc.), already carries a
-- seeded FY 2026-2027 row, and needs no schema change here.

ALTER TABLE cpf_trust_member_ledger_entries
    ADD COLUMN IF NOT EXISTS is_provisional_rate BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS rate_applied NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS rate_source_fin_year VARCHAR(9);

-- Widen V74's entry_type CHECK to add INTERIM_SETTLEMENT_INTEREST alongside the existing values.
ALTER TABLE cpf_trust_member_ledger_entries DROP CONSTRAINT IF EXISTS ck_cpf_ledger_entry_type;
ALTER TABLE cpf_trust_member_ledger_entries ADD CONSTRAINT ck_cpf_ledger_entry_type CHECK (entry_type IN
    ('OPENING_BALANCE', 'PAYROLL_MONTHLY', 'DA_ARREAR', 'TRANSFER_IN', 'LOAN_WITHDRAWAL', 'LOAN_REPAYMENT',
     'ANNUAL_INTEREST', 'INTERIM_SETTLEMENT_INTEREST', 'FINAL_SETTLEMENT'));
