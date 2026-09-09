-- IF NOT EXISTS throughout this migration: employee_incoming_fund_transfers, cpf_loan_applications,
-- cpf_trust_member_ledger_entries, and cpf_annual_interest_runs were already created directly against the
-- shared dev database by other tooling before this migration existed (same situation as V19's
-- state_master/V66's payroll masters, and uan_no below) - all four tables were confirmed empty (0 rows)
-- when this migration was written, so this reproduces that exact live shape (CREATE TABLE IF NOT EXISTS)
-- and additionally backfills the CHECK constraints the live tables were missing, rather than assuming a
-- shape and colliding with what is already live.

ALTER TABLE employees ADD COLUMN IF NOT EXISTS uan_no VARCHAR(20);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS prior_qualifying_service_days INT NOT NULL DEFAULT 0;

-- A prior employer's/Trust's PF+Pension corpus being transferred in for a newly-joined employee.
CREATE TABLE IF NOT EXISTS employee_incoming_fund_transfers (
    id                             BIGSERIAL     PRIMARY KEY,
    transfer_reference_no          VARCHAR(50)   NOT NULL UNIQUE,
    employee_id                    BIGINT        NOT NULL REFERENCES employees (id),
    past_service_record_id         BIGINT        REFERENCES employee_past_service_records (id),
    source_organization_name       VARCHAR(200)  NOT NULL,
    source_organization_type       VARCHAR(50)   NOT NULL,
    transfer_type                  VARCHAR(30)   NOT NULL,
    relieving_date                 DATE          NOT NULL,
    jci_joining_date               DATE          NOT NULL,
    payment_mode                   VARCHAR(20)   NOT NULL,
    instrument_or_utr_no           VARCHAR(100)  NOT NULL,
    instrument_date                DATE          NOT NULL,
    bank_realization_date          DATE          NOT NULL,
    bank_account_code              VARCHAR(30)   NOT NULL,

    ee_cpf_principal                NUMERIC(12,2) NOT NULL DEFAULT 0,
    ee_cpf_interest                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    er_jcpf_principal               NUMERIC(12,2) NOT NULL DEFAULT 0,
    er_jcpf_interest                NUMERIC(12,2) NOT NULL DEFAULT 0,
    vpf_principal                   NUMERIC(12,2) NOT NULL DEFAULT 0,
    vpf_interest                    NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cpf_transferred           NUMERIC(14,2) NOT NULL,

    pension_scheme                  VARCHAR(10)   NOT NULL,
    pension_corpus_amount           NUMERIC(12,2) NOT NULL DEFAULT 0,
    pran_or_ppo_no                  VARCHAR(50),
    past_qualifying_service_years   INT           NOT NULL DEFAULT 0,
    past_qualifying_service_days    INT           NOT NULL DEFAULT 0,
    gratuity_transferred_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_gratuity_service_counted     BOOLEAN       NOT NULL DEFAULT TRUE,

    annexure_k_doc_ref              VARCHAR(100),
    sanction_order_no               VARCHAR(100),
    sanction_date                   DATE,
    status                          VARCHAR(30)   NOT NULL DEFAULT 'SUBMITTED',
    credited_at                     TIMESTAMPTZ,
    credited_by                     BIGINT        REFERENCES employees (id),
    created_by                      BIGINT        REFERENCES employees (id),
    created_at                      TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_inc_transfer_emp ON employee_incoming_fund_transfers (employee_id, status);

DO $$ BEGIN
    ALTER TABLE employee_incoming_fund_transfers ADD CONSTRAINT ck_incoming_transfer_source_org_type CHECK (source_organization_type IN
        ('CENTRAL_GOVT', 'STATE_GOVT', 'CENTRAL_PSU', 'STATE_PSU', 'AUTONOMOUS_BODY', 'DEFENCE_ARMY_NAVY_AIRFORCE', 'PRIVATE_SECTOR', 'OTHER'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE employee_incoming_fund_transfers ADD CONSTRAINT ck_incoming_transfer_type CHECK (transfer_type IN ('PF_ONLY', 'PENSION_ONLY', 'PF_AND_PENSION'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE employee_incoming_fund_transfers ADD CONSTRAINT ck_incoming_transfer_payment_mode CHECK (payment_mode IN ('CHEQUE', 'DEMAND_DRAFT', 'NEFT', 'RTGS', 'OTHER'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE employee_incoming_fund_transfers ADD CONSTRAINT ck_incoming_transfer_status CHECK (status IN ('SUBMITTED', 'VERIFIED_BY_TRUST', 'CREDITED_TO_LEDGER', 'REJECTED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Prior-service career event recognizing a credited incoming fund transfer.
ALTER TABLE employee_service_book ADD COLUMN IF NOT EXISTS incoming_transfer_id BIGINT REFERENCES employee_incoming_fund_transfers (id);

-- CPF Trust-administered loans/withdrawals against a member's own CPF balance - deliberately separate
-- from the general-purpose employee_loans (HBA/Term/Emergency/Festival advances etc.), since a Trust loan
-- draws down the member's own Trust corpus (see cpf_trust_member_ledger_entries.loan_id below) under CPF
-- Trust Rules, not general company loan policy.
CREATE TABLE IF NOT EXISTS cpf_loan_applications (
    id                          BIGSERIAL     PRIMARY KEY,
    loan_application_no         VARCHAR(50)   NOT NULL UNIQUE,
    employee_id                 BIGINT        NOT NULL REFERENCES employees (id),
    loan_type                   VARCHAR(30)   NOT NULL,
    purpose                     VARCHAR(50)   NOT NULL,
    applied_amount               NUMERIC(12,2) NOT NULL,
    sanctioned_amount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    sanction_order_no            VARCHAR(100),
    sanction_date                 DATE,
    monthly_recovery_principal   NUMERIC(12,2) NOT NULL DEFAULT 0,
    monthly_recovery_interest    NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_installments            INT           NOT NULL DEFAULT 0,
    recovered_installments        INT           NOT NULL DEFAULT 0,
    outstanding_balance           NUMERIC(12,2) NOT NULL DEFAULT 0,
    status                        VARCHAR(30)   NOT NULL DEFAULT 'APPLIED',
    disbursed_at                  TIMESTAMPTZ,
    created_at                    TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cpf_loan_emp ON cpf_loan_applications (employee_id, status);

DO $$ BEGIN
    ALTER TABLE cpf_loan_applications ADD CONSTRAINT ck_cpf_loan_type CHECK (loan_type IN ('REFUNDABLE_LOAN', 'NON_REFUNDABLE_WITHDRAWAL'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE cpf_loan_applications ADD CONSTRAINT ck_cpf_loan_status CHECK (status IN ('APPLIED', 'SANCTIONED', 'DISBURSED', 'CLOSED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- One immutable transaction row per member's CPF Trust passbook - see CpfTrustMemberLedgerEntry's own
-- javadoc for why this is a full transactional ledger rather than a current-balance-only cache
-- (cpf_balance_ledgers, V10, stays as-is for whatever already reads it).
CREATE TABLE IF NOT EXISTS cpf_trust_member_ledger_entries (
    id                      BIGSERIAL     PRIMARY KEY,
    employee_id             BIGINT        NOT NULL REFERENCES employees (id),
    fin_year                VARCHAR(9)    NOT NULL,
    sal_month               INT,
    sal_year                INT,
    value_date              DATE          NOT NULL,
    entry_type              VARCHAR(30)   NOT NULL,

    ee_share_credit         NUMERIC(12,2) NOT NULL DEFAULT 0,
    ee_share_debit          NUMERIC(12,2) NOT NULL DEFAULT 0,
    er_share_credit         NUMERIC(12,2) NOT NULL DEFAULT 0,
    er_share_debit          NUMERIC(12,2) NOT NULL DEFAULT 0,
    vpf_credit               NUMERIC(12,2) NOT NULL DEFAULT 0,
    vpf_debit                NUMERIC(12,2) NOT NULL DEFAULT 0,
    interest_credit          NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_credit              NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_debit                NUMERIC(14,2) NOT NULL DEFAULT 0,

    running_ee_balance        NUMERIC(14,2) NOT NULL DEFAULT 0,
    running_er_balance        NUMERIC(14,2) NOT NULL DEFAULT 0,
    running_vpf_balance       NUMERIC(14,2) NOT NULL DEFAULT 0,
    running_total_balance     NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- The legacy payroll_runs table (not payroll_batches) - matches the live schema's own choice, and
    -- LoanRepayment.payroll_run_id's existing precedent for how this app's payroll-adjacent tables link
    -- back to a cycle. Populated on a best-effort basis (PayrollRunRepository.findByCycleYearAndCycleMonth)
    -- since the actual CPF/VPF/JCPF amounts synced here are read from the newer PayrollBatch-linked
    -- payroll_monthly_head_items/payroll_monthly_statutory_items - the only place this codebase actually
    -- computes and stores them (see CpfLedgerSyncService's own javadoc) - not from payroll_runs itself.
    payroll_run_id             BIGINT        REFERENCES payroll_runs (id),
    transfer_id                BIGINT        REFERENCES employee_incoming_fund_transfers (id),
    loan_id                    BIGINT        REFERENCES cpf_loan_applications (id),
    reference_doc_no           VARCHAR(100),
    remarks                    TEXT,
    created_at                 TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cpf_ledger_lookup ON cpf_trust_member_ledger_entries (employee_id, fin_year, value_date);
CREATE INDEX IF NOT EXISTS idx_cpf_ledger_run ON cpf_trust_member_ledger_entries (payroll_run_id) WHERE payroll_run_id IS NOT NULL;

DO $$ BEGIN
    ALTER TABLE cpf_trust_member_ledger_entries ADD CONSTRAINT ck_cpf_ledger_entry_type CHECK (entry_type IN
        ('OPENING_BALANCE', 'PAYROLL_MONTHLY', 'DA_ARREAR', 'TRANSFER_IN', 'LOAN_WITHDRAWAL', 'LOAN_REPAYMENT', 'ANNUAL_INTEREST', 'FINAL_SETTLEMENT'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE cpf_trust_member_ledger_entries ADD CONSTRAINT ck_cpf_ledger_fin_year CHECK (fin_year ~ '^[0-9]{4}-[0-9]{4}$');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- One year-end summary row per financial year (fin_year is UNIQUE - a corrected re-run must update this
-- row in place, e.g. moving it to SUPERSEDED and inserting fresh interest credits, rather than inserting
-- a second row for the same FY).
CREATE TABLE IF NOT EXISTS cpf_annual_interest_runs (
    id                              BIGSERIAL     PRIMARY KEY,
    fin_year                        VARCHAR(9)    NOT NULL UNIQUE,
    declared_interest_rate          NUMERIC(5,2)  NOT NULL,
    interest_order_no               VARCHAR(100)  NOT NULL,
    interest_order_date             DATE          NOT NULL,
    run_date                        DATE          NOT NULL DEFAULT CURRENT_DATE,
    total_members_processed         INT           NOT NULL DEFAULT 0,
    total_interest_credited_ee      NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_interest_credited_er      NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_interest_credited_vpf     NUMERIC(14,2) NOT NULL DEFAULT 0,
    status                          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    posted_by                       BIGINT        REFERENCES employees (id),
    posted_at                       TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP
);

DO $$ BEGIN
    ALTER TABLE cpf_annual_interest_runs ADD CONSTRAINT ck_cpf_interest_run_status CHECK (status IN ('DRAFT', 'POSTED', 'SUPERSEDED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE cpf_annual_interest_runs ADD CONSTRAINT ck_cpf_interest_run_fin_year CHECK (fin_year ~ '^[0-9]{4}-[0-9]{4}$');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
