-- CPF Interest Management module: extends the year-end run register (cpf_annual_interest_runs, V74) from a
-- single-shot "compute-and-post" record into a full Calculate -> Approve/Post -> Reverse -> Recalculate
-- workflow register, and adds the ledger-side plumbing (ANNUAL_INTEREST_REVERSAL entry type, a run linkage
-- column) needed to reverse a posted annual run without ever deleting the original ANNUAL_INTEREST rows -
-- see CpfInterestRunService's own javadoc for the workflow this supports.

-- Every ledger row an interest run produced (the ANNUAL_INTEREST credits, or later an
-- ANNUAL_INTEREST_REVERSAL debit) is linked back to the run that created it - CpfInterestRunService.reverseRun()
-- uses this to find exactly the rows a given run posted, rather than re-deriving them from fin_year/value_date
-- (which would be ambiguous once a member-scoped correction run exists alongside an all-members run for the
-- same FY).
ALTER TABLE cpf_trust_member_ledger_entries
    ADD COLUMN IF NOT EXISTS interest_run_id BIGINT REFERENCES cpf_annual_interest_runs (id);
CREATE INDEX IF NOT EXISTS idx_cpf_ledger_interest_run ON cpf_trust_member_ledger_entries (interest_run_id) WHERE interest_run_id IS NOT NULL;

ALTER TABLE cpf_trust_member_ledger_entries DROP CONSTRAINT IF EXISTS ck_cpf_ledger_entry_type;
ALTER TABLE cpf_trust_member_ledger_entries ADD CONSTRAINT ck_cpf_ledger_entry_type CHECK (entry_type IN
    ('OPENING_BALANCE', 'PAYROLL_MONTHLY', 'DA_ARREAR', 'TRANSFER_IN', 'LOAN_WITHDRAWAL', 'LOAN_REPAYMENT',
     'ANNUAL_INTEREST', 'ANNUAL_INTEREST_REVERSAL', 'INTERIM_SETTLEMENT_INTEREST', 'FINAL_SETTLEMENT'));

-- Workflow/scope columns. scope+member_employee_id let the same fin_year carry either one ALL_MEMBERS run
-- or any number of SELECTED_MEMBER correction runs (Part 25/26 of the module spec) without colliding.
ALTER TABLE cpf_annual_interest_runs
    ADD COLUMN IF NOT EXISTS scope               VARCHAR(20) NOT NULL DEFAULT 'ALL_MEMBERS',
    ADD COLUMN IF NOT EXISTS member_employee_id   BIGINT REFERENCES employees (id),
    ADD COLUMN IF NOT EXISTS calculated_by        BIGINT REFERENCES employees (id),
    ADD COLUMN IF NOT EXISTS calculated_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reversed_by          BIGINT REFERENCES employees (id),
    ADD COLUMN IF NOT EXISTS reversed_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS data_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS remarks              TEXT;

-- V74 declared fin_year UNIQUE inline (auto-named cpf_annual_interest_runs_fin_year_key) on the assumption
-- of one row per FY forever. The workflow needs that row to become non-unique once REVERSED/FAILED runs
-- accumulate history for the same FY, and needs a member-scoped run to coexist with the FY's all-members
-- run. Replace it with two partial unique indexes: at most one *active* (not reversed/failed) run per
-- fin_year+scope(+member).
ALTER TABLE cpf_annual_interest_runs DROP CONSTRAINT IF EXISTS cpf_annual_interest_runs_fin_year_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cpf_interest_run_active_all_members
    ON cpf_annual_interest_runs (fin_year)
    WHERE scope = 'ALL_MEMBERS' AND status NOT IN ('REVERSED', 'FAILED');

CREATE UNIQUE INDEX IF NOT EXISTS ux_cpf_interest_run_active_member
    ON cpf_annual_interest_runs (fin_year, member_employee_id)
    WHERE scope = 'SELECTED_MEMBER' AND status NOT IN ('REVERSED', 'FAILED');

DO $$ BEGIN
    ALTER TABLE cpf_annual_interest_runs ADD CONSTRAINT ck_cpf_interest_run_scope CHECK (
        (scope = 'ALL_MEMBERS' AND member_employee_id IS NULL) OR
        (scope = 'SELECTED_MEMBER' AND member_employee_id IS NOT NULL));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Widen the status lifecycle: CALCULATED (preview persisted, ledger untouched) -> POSTED (ledger entries
-- inserted) -> REVERSED (reversal entries inserted, original entries preserved) or FAILED. DRAFT/SUPERSEDED
-- (V74's original two-state model) are dropped - no run has ever reached POSTED yet (checked against the
-- live dev DB before writing this migration), so there is no historical data in either state to preserve.
ALTER TABLE cpf_annual_interest_runs DROP CONSTRAINT IF EXISTS ck_cpf_interest_run_status;
ALTER TABLE cpf_annual_interest_runs ADD CONSTRAINT ck_cpf_interest_run_status CHECK (status IN
    ('CALCULATED', 'POSTED', 'REVERSED', 'FAILED'));
