-- JCIECCS (JCI Employees' Co-Operative Credit Society) Lifecycle Engine schema. This migration
-- reproduces, verbatim, a schema that was already applied directly against this shared dev database by
-- another in-progress session before this migration file existed (confirmed: not present in
-- flyway_schema_history, no corresponding file anywhere in this git working tree, 2 seed rows already
-- present). All CREATE/INSERT statements here are idempotent (IF NOT EXISTS / WHERE NOT EXISTS) so this
-- migration is a clean no-op against that already-applied state and a faithful from-scratch build on any
-- other environment. Every table/column/constraint name, type, and default below was captured via
-- `pg_dump --schema-only` against the live tables - nothing here is guessed. Payroll remains the
-- execution engine: it imports the LOCKED collection snapshot this module produces as read-only
-- deductions, never edits it, and posts an async debit-confirmation callback this module reconciles
-- against. Entirely separate from the generic, placeholder V10 employee_loans/loan_type_master skeleton
-- (COOP_TERM/COOP_EMERGENCY codes seeded there, zero live rows, left untouched).

-- 1. hrms_payroll_cycle - the 26th-of-previous-month-to-25th-of-current-month collection window.
-- Independent of payroll_batches (calendar-month sal_month/sal_year) and the older payroll_runs.
CREATE TABLE IF NOT EXISTS hrms_payroll_cycle (
    id            BIGSERIAL PRIMARY KEY,
    cycle_code    VARCHAR(7)  NOT NULL,
    period_start  DATE        NOT NULL,
    period_end    DATE        NOT NULL,
    cycle_status  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT hrms_payroll_cycle_cycle_code_key UNIQUE (cycle_code),
    CONSTRAINT hrms_payroll_cycle_period_start_key UNIQUE (period_start),
    CONSTRAINT hrms_payroll_cycle_period_end_key UNIQUE (period_end),
    CONSTRAINT chk_hrms_cycle_bounds CHECK (period_start < period_end),
    CONSTRAINT chk_hrms_cycle_status CHECK (cycle_status IN ('OPEN', 'LOCKED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_hrms_cycle_dates ON hrms_payroll_cycle (period_start, period_end);

-- 2. jcieccs_member - one row per member, mutated in place as balances change (not a per-change history
-- table - employee_id/membership_code are blanket-unique, so this is a true 1:1 with employees).
-- effective_from/effective_to bracket active society membership itself (e.g. resignation sets
-- effective_to); version is a plain optimistic-lock counter.
CREATE TABLE IF NOT EXISTS jcieccs_member (
    id                     BIGSERIAL PRIMARY KEY,
    employee_id            BIGINT      NOT NULL,
    membership_code        VARCHAR(50) NOT NULL,
    membership_date        DATE        NOT NULL,
    membership_status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    share_balance          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    fund_balance           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    security_balance       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    thrift_monthly_amount  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    effective_from         DATE        NOT NULL DEFAULT CURRENT_DATE,
    effective_to           DATE        NULL,
    version                BIGINT      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             BIGINT      NULL,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             BIGINT      NULL,

    CONSTRAINT jcieccs_member_employee_id_key UNIQUE (employee_id),
    CONSTRAINT jcieccs_member_membership_code_key UNIQUE (membership_code),
    CONSTRAINT chk_jcieccs_member_status CHECK (membership_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT chk_jcieccs_balances_nonneg CHECK (share_balance >= 0 AND fund_balance >= 0 AND security_balance >= 0),
    CONSTRAINT chk_jcieccs_thrift_nonneg CHECK (thrift_monthly_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_mem_emp ON jcieccs_member (employee_id);

-- 3. jcieccs_loan_product - one mutable row per product code (Term/Emergency); no history table -
-- effective_from/effective_to describe this single row's own validity window, not a revision chain.
CREATE TABLE IF NOT EXISTS jcieccs_loan_product (
    id                    BIGSERIAL PRIMARY KEY,
    product_code          VARCHAR(20)  NOT NULL,
    product_name          VARCHAR(100) NOT NULL,
    loan_prefix           VARCHAR(5)   NOT NULL,
    max_amount            NUMERIC(14, 2) NOT NULL,
    max_tenure_months     INT          NOT NULL,
    repayment_start_rule  VARCHAR(40)  NOT NULL,
    interest_method       VARCHAR(40)  NOT NULL DEFAULT 'REDUCING_MONTHLY',
    principal_multiple    INT          NOT NULL DEFAULT 10,
    topup_allowed         BOOLEAN      NOT NULL DEFAULT FALSE,
    topup_min_repaid_pct  NUMERIC(5, 2) NULL DEFAULT 0.00,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    effective_from        DATE         NOT NULL,
    effective_to          DATE         NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT jcieccs_loan_product_product_code_key UNIQUE (product_code),
    CONSTRAINT jcieccs_loan_product_loan_prefix_key UNIQUE (loan_prefix),
    CONSTRAINT chk_jcieccs_prod_amount CHECK (max_amount > 0),
    CONSTRAINT chk_jcieccs_prod_tenure CHECK (max_tenure_months > 0),
    CONSTRAINT chk_jcieccs_prod_mult CHECK (principal_multiple > 0)
);

INSERT INTO jcieccs_loan_product (product_code, product_name, loan_prefix, max_amount, max_tenure_months,
                                   repayment_start_rule, interest_method, principal_multiple, topup_allowed,
                                   topup_min_repaid_pct, active, effective_from)
SELECT 'TERM', 'Term Loan', 'TE', 500000.00, 60, 'DISBURSEMENT_CYCLE', 'REDUCING_MONTHLY', 10, TRUE, 60.00, TRUE, CURRENT_DATE
WHERE NOT EXISTS (SELECT 1 FROM jcieccs_loan_product WHERE product_code = 'TERM');

INSERT INTO jcieccs_loan_product (product_code, product_name, loan_prefix, max_amount, max_tenure_months,
                                   repayment_start_rule, interest_method, principal_multiple, topup_allowed,
                                   topup_min_repaid_pct, active, effective_from)
SELECT 'EMERGENCY', 'Emergency Loan', 'EM', 70000.00, 10, 'NEXT_CYCLE', 'REDUCING_MONTHLY', 10, FALSE, 0.00, TRUE, CURRENT_DATE
WHERE NOT EXISTS (SELECT 1 FROM jcieccs_loan_product WHERE product_code = 'EMERGENCY');

-- 4. jcieccs_loan_interest_rate - true effective-dated rate history per product (no uniqueness
-- constraint at the DB level - "current row" is an application-level query, effective_to IS NULL).
CREATE TABLE IF NOT EXISTS jcieccs_loan_interest_rate (
    id                    BIGSERIAL PRIMARY KEY,
    loan_product_id       BIGINT      NOT NULL REFERENCES jcieccs_loan_product (id),
    annual_interest_rate  NUMERIC(8, 4) NOT NULL,
    effective_from        DATE        NOT NULL,
    effective_to          DATE        NULL,
    approved_reference    VARCHAR(200) NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            BIGINT      NULL,

    CONSTRAINT chk_jcieccs_int_rate CHECK (annual_interest_rate >= 0)
);

INSERT INTO jcieccs_loan_interest_rate (loan_product_id, annual_interest_rate, effective_from, approved_reference)
SELECT p.id, 10.0000, CURRENT_DATE, 'Default seed rate - not confirmed JCIECCS board policy'
FROM jcieccs_loan_product p
WHERE NOT EXISTS (SELECT 1 FROM jcieccs_loan_interest_rate r WHERE r.loan_product_id = p.id);

-- 5. Sequences backing human-readable loan issue ids (TE-000001/EM-000001) via nextval(), never MAX()+1 -
-- same pattern as onboarding_draft_seq (V25) + EmployeeOnboardingDraftRepository.nextDraftCodeSequence().
CREATE SEQUENCE IF NOT EXISTS jcieccs_te_loan_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS jcieccs_em_loan_seq START WITH 1 INCREMENT BY 1;

-- 6. jcieccs_loan - the loan itself. monthly_principal_inst is INTEGER (whole rupees; the %10=0 check
-- enforces the mandatory ₹10-multiple rule at the DB level, not just in Java).
CREATE TABLE IF NOT EXISTS jcieccs_loan (
    id                        BIGSERIAL PRIMARY KEY,
    member_id                 BIGINT      NOT NULL REFERENCES jcieccs_member (id),
    loan_product_id           BIGINT      NOT NULL REFERENCES jcieccs_loan_product (id),
    loan_issue_id             VARCHAR(30) NOT NULL,
    application_date          DATE        NULL,
    sanction_date             DATE        NOT NULL,
    disbursement_date         DATE        NOT NULL,
    disbursement_cycle_id     BIGINT      NOT NULL REFERENCES hrms_payroll_cycle (id),
    sanctioned_amount         NUMERIC(14, 2) NOT NULL,
    disbursed_amount          NUMERIC(14, 2) NOT NULL,
    tenure_months             INT         NOT NULL,
    annual_interest_rate      NUMERIC(8, 4) NOT NULL,
    monthly_principal_inst    INT         NOT NULL,
    outstanding_principal     NUMERIC(14, 2) NOT NULL,
    status                    VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    parent_loan_id            BIGINT      NULL REFERENCES jcieccs_loan (id),
    restructuring_count       INT         NOT NULL DEFAULT 0,
    topup_count               INT         NOT NULL DEFAULT 0,
    closed_date               DATE        NULL,
    closure_reason            VARCHAR(100) NULL,
    version                   BIGINT      NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                BIGINT      NULL,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                BIGINT      NULL,

    CONSTRAINT jcieccs_loan_loan_issue_id_key UNIQUE (loan_issue_id),
    CONSTRAINT chk_jcieccs_loan_amt CHECK (sanctioned_amount > 0 AND disbursed_amount > 0),
    CONSTRAINT chk_jcieccs_loan_tenure CHECK (tenure_months > 0),
    CONSTRAINT chk_jcieccs_loan_inst CHECK (monthly_principal_inst > 0 AND (monthly_principal_inst % 10) = 0),
    CONSTRAINT chk_jcieccs_loan_out CHECK (outstanding_principal >= 0),
    CONSTRAINT chk_jcieccs_loan_status CHECK (status IN ('PENDING', 'ACTIVE', 'RESTRUCTURED', 'CLOSED', 'DEFAULTED', 'WRITTEN_OFF'))
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_loan_mem ON jcieccs_loan (member_id, status);

-- 7. jcieccs_loan_schedule - pre-generated, per-installment amortization schedule. principal_due is
-- INTEGER for the same ₹10-multiple reason as jcieccs_loan.monthly_principal_inst.
CREATE TABLE IF NOT EXISTS jcieccs_loan_schedule (
    id                     BIGSERIAL PRIMARY KEY,
    loan_id                BIGINT      NOT NULL REFERENCES jcieccs_loan (id),
    installment_no         INT         NOT NULL,
    cycle_id               BIGINT      NOT NULL REFERENCES hrms_payroll_cycle (id),
    opening_principal      NUMERIC(14, 2) NOT NULL,
    principal_due          INT         NOT NULL DEFAULT 0,
    interest_due           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_due              NUMERIC(14, 2) GENERATED ALWAYS AS (principal_due::numeric + interest_due) STORED,
    principal_paid         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    interest_paid          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_paid             NUMERIC(14, 2) GENERATED ALWAYS AS (principal_paid + interest_paid) STORED,
    principal_outstanding  NUMERIC(14, 2) NOT NULL,
    status                 VARCHAR(30) NOT NULL DEFAULT 'DUE',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT jcieccs_loan_schedule_loan_id_installment_no_key UNIQUE (loan_id, installment_no),
    CONSTRAINT chk_jcieccs_sched_status CHECK (status IN ('FUTURE', 'DUE', 'PARTIAL', 'PAID', 'OVERDUE', 'RESTRUCTURED', 'WAIVED'))
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_sched_cycle ON jcieccs_loan_schedule (cycle_id, status);

-- 8. jcieccs_collection_batch - the locked, immutable per-payroll-run snapshot. payroll_run_id is a
-- plain opaque string business key supplied by Payroll (no FK to payroll_batches - keeps this module
-- decoupled from that schema's own internals; Payroll and JCIECCS only agree on this string).
CREATE TABLE IF NOT EXISTS jcieccs_collection_batch (
    id                     BIGSERIAL PRIMARY KEY,
    payroll_run_id         VARCHAR(50) NOT NULL,
    cycle_id               BIGINT      NOT NULL REFERENCES hrms_payroll_cycle (id),
    calculated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calculated_by          BIGINT      NULL,
    batch_status           VARCHAR(30) NOT NULL DEFAULT 'LOCKED',
    locked_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    debit_confirmed_at     TIMESTAMPTZ NULL,
    total_expected_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_debited_amount   NUMERIC(14, 2) NOT NULL DEFAULT 0,

    CONSTRAINT jcieccs_collection_batch_payroll_run_id_key UNIQUE (payroll_run_id),
    CONSTRAINT chk_jcieccs_batch_status CHECK (batch_status IN ('LOCKED', 'PROCESSED', 'REOPENED'))
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_batch_cycle ON jcieccs_collection_batch (cycle_id);

-- 9. jcieccs_collection_detail - one row per employee per collection batch; the read-only, locked line
-- item Payroll imports verbatim as deductions. term_principal/emergency_principal are INTEGER for the
-- same ₹10-multiple reason as elsewhere in this schema.
CREATE TABLE IF NOT EXISTS jcieccs_collection_detail (
    id                      BIGSERIAL PRIMARY KEY,
    batch_id                BIGINT      NOT NULL REFERENCES jcieccs_collection_batch (id),
    employee_id             BIGINT      NOT NULL,
    member_id               BIGINT      NOT NULL REFERENCES jcieccs_member (id),
    thrift_amount            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    term_loan_id             BIGINT      NULL REFERENCES jcieccs_loan (id),
    term_principal            INT         NOT NULL DEFAULT 0,
    term_interest             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    emergency_loan_id         BIGINT      NULL REFERENCES jcieccs_loan (id),
    emergency_principal       INT         NOT NULL DEFAULT 0,
    emergency_interest        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_snapshot_amount     NUMERIC(14, 2) GENERATED ALWAYS AS
        (thrift_amount + term_principal::numeric + term_interest + emergency_principal::numeric + emergency_interest) STORED,
    debit_status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_DEBIT',
    actual_debited_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    payroll_transaction_id    VARCHAR(100) NULL,
    posted_at                 TIMESTAMPTZ NULL,

    CONSTRAINT jcieccs_collection_detail_batch_id_employee_id_key UNIQUE (batch_id, employee_id),
    CONSTRAINT chk_jcieccs_detail_status
        CHECK (debit_status IN ('PENDING_DEBIT', 'DEBIT_SUCCESS', 'DEBIT_PARTIAL', 'DEBIT_FAILED', 'REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_detail_batch ON jcieccs_collection_detail (batch_id, debit_status);

-- 10. jcieccs_loan_repayment - the actual posted repayment ledger.
CREATE TABLE IF NOT EXISTS jcieccs_loan_repayment (
    id                    BIGSERIAL PRIMARY KEY,
    loan_id               BIGINT      NOT NULL REFERENCES jcieccs_loan (id),
    schedule_id           BIGINT      NULL REFERENCES jcieccs_loan_schedule (id),
    collection_detail_id  BIGINT      NULL REFERENCES jcieccs_collection_detail (id),
    employee_id           BIGINT      NOT NULL,
    repayment_date        DATE        NOT NULL,
    cycle_id              BIGINT      NOT NULL REFERENCES hrms_payroll_cycle (id),
    source                VARCHAR(30) NOT NULL,
    reference_id          VARCHAR(100) NULL,
    principal_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    interest_amount       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount          NUMERIC(14, 2) GENERATED ALWAYS AS (principal_amount + interest_amount) STORED,
    remarks               VARCHAR(500) NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            BIGINT      NULL,

    CONSTRAINT chk_jcieccs_rep_source CHECK (source IN ('PAYROLL', 'CASH', 'ADJUSTMENT')),
    CONSTRAINT chk_jcieccs_rep_amounts
        CHECK (principal_amount >= 0 AND interest_amount >= 0 AND (principal_amount + interest_amount) > 0)
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_rep_loan ON jcieccs_loan_repayment (loan_id, cycle_id);

-- 11. jcieccs_thrift_transaction - the thrift ledger, independent of any loan.
CREATE TABLE IF NOT EXISTS jcieccs_thrift_transaction (
    id                    BIGSERIAL PRIMARY KEY,
    member_id             BIGINT      NOT NULL REFERENCES jcieccs_member (id),
    employee_id           BIGINT      NOT NULL,
    collection_detail_id  BIGINT      NULL REFERENCES jcieccs_collection_detail (id),
    transaction_date      DATE        NOT NULL,
    cycle_id              BIGINT      NOT NULL REFERENCES hrms_payroll_cycle (id),
    transaction_type      VARCHAR(30) NOT NULL,
    amount                NUMERIC(14, 2) NOT NULL,
    source                VARCHAR(30) NOT NULL,
    balance_after         NUMERIC(14, 2) NOT NULL,
    remarks               VARCHAR(500) NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            BIGINT      NULL,

    CONSTRAINT chk_jcieccs_th_type CHECK (transaction_type IN ('CONTRIBUTION', 'ADJUSTMENT', 'REFUND', 'OPENING_BALANCE')),
    CONSTRAINT chk_jcieccs_th_source CHECK (source IN ('PAYROLL', 'CASH', 'MIGRATION')),
    CONSTRAINT chk_jcieccs_th_amt CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_thrift_mem ON jcieccs_thrift_transaction (member_id, transaction_date);

-- 12. jcieccs_lifecycle_event - free-form audit trail across every JCIECCS entity kind.
CREATE TABLE IF NOT EXISTS jcieccs_lifecycle_event (
    id            BIGSERIAL PRIMARY KEY,
    entity_type   VARCHAR(40) NOT NULL,
    entity_id     BIGINT      NOT NULL,
    event_type    VARCHAR(60) NOT NULL,
    event_date    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_value     JSONB       NULL,
    new_value     JSONB       NULL,
    reference_id  VARCHAR(100) NULL,
    performed_by  BIGINT      NULL,
    remarks       VARCHAR(1000) NULL
);
