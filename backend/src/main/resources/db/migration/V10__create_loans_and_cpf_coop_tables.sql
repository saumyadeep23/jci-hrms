CREATE TABLE loan_type_master (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(20)  NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    interest_rate_annual  NUMERIC(5,2) NOT NULL,
    max_installments      INTEGER      NOT NULL,
    is_reducing_balance   BOOLEAN      NOT NULL DEFAULT true,
    is_active             BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,

    CONSTRAINT ck_loan_type_master_code
        CHECK (code IN ('FESTIVAL', 'FLOOD', 'CPF_SECURED', 'COOP_TERM', 'COOP_EMERGENCY', 'HBL', 'NON_REFUNDABLE_PF'))
);

CREATE UNIQUE INDEX uq_loan_type_master_code_active ON loan_type_master (code) WHERE deleted_at IS NULL;

-- Placeholder rates/terms, not confirmed JCI loan policy - see LoanService
-- javadoc. Festival/Flood advances are typically interest-free short-term
-- advances rather than amortizing loans; NON_REFUNDABLE_PF is a one-time
-- withdrawal, not a repaid loan, hence max_installments=1 and 0% rate.
INSERT INTO loan_type_master (code, name, interest_rate_annual, max_installments, is_reducing_balance) VALUES
    ('FESTIVAL',           'Festival Advance',              0.00, 10,  false),
    ('FLOOD',               'Flood/Calamity Advance',        0.00, 10,  false),
    ('CPF_SECURED',         'CPF Secured Loan',              8.00, 48,  true),
    ('COOP_TERM',           'Co-operative Term Loan',        10.00, 60, true),
    ('COOP_EMERGENCY',      'Co-operative Emergency Loan',   10.00, 24, true),
    ('HBL',                 'House Building Loan',           7.50, 180, true),
    ('NON_REFUNDABLE_PF',   'Non-Refundable PF Withdrawal',  0.00, 1,   false);

CREATE TABLE employee_loans (
    id                      BIGSERIAL PRIMARY KEY,
    loan_type_id            BIGINT        NOT NULL REFERENCES loan_type_master (id),
    employee_id             BIGINT        NOT NULL REFERENCES employees (id),
    loan_account_number     VARCHAR(50)   NOT NULL,
    principal_amount        NUMERIC(12,2) NOT NULL,
    interest_rate           NUMERIC(5,2)  NOT NULL,
    total_installments      INTEGER       NOT NULL,
    remaining_installments  INTEGER       NOT NULL,
    outstanding_principal   NUMERIC(12,2) NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'SANCTIONED',
    sanction_date           DATE          NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,

    CONSTRAINT ck_employee_loans_status CHECK (status IN ('SANCTIONED', 'DISBURSED', 'ACTIVE', 'CLOSED', 'FORECLOSED'))
);

CREATE UNIQUE INDEX uq_employee_loans_account_number_active ON employee_loans (loan_account_number) WHERE deleted_at IS NULL;
CREATE INDEX ix_employee_loans_employee_id ON employee_loans (employee_id);

CREATE TABLE loan_repayments (
    id                     BIGSERIAL PRIMARY KEY,
    loan_id                BIGINT        NOT NULL REFERENCES employee_loans (id),
    repayment_source       VARCHAR(20)   NOT NULL,
    amount                 NUMERIC(12,2) NOT NULL,
    principal_component    NUMERIC(12,2) NOT NULL,
    interest_component     NUMERIC(12,2) NOT NULL,
    payment_date           DATE          NOT NULL,
    payroll_run_id         BIGINT REFERENCES payroll_runs (id),
    transaction_reference  VARCHAR(100),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_loan_repayments_source CHECK (repayment_source IN ('PAYROLL_DEDUCTION', 'CASH_DEPOSIT', 'FORECLOSURE'))
);

CREATE INDEX ix_loan_repayments_loan_id ON loan_repayments (loan_id);

CREATE TABLE cpf_balance_ledgers (
    id                            BIGSERIAL PRIMARY KEY,
    employee_id                   BIGINT        NOT NULL REFERENCES employees (id),
    employee_fund_balance         NUMERIC(12,2) NOT NULL DEFAULT 0,
    employer_fund_balance         NUMERIC(12,2) NOT NULL DEFAULT 0,
    vpf_balance                   NUMERIC(12,2) NOT NULL DEFAULT 0,
    last_interest_credited_date   DATE,
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_cpf_balance_ledgers_employee_id UNIQUE (employee_id),
    CONSTRAINT ck_cpf_balance_ledgers_non_negative
        CHECK (employee_fund_balance >= 0 AND employer_fund_balance >= 0 AND vpf_balance >= 0)
);

CREATE TABLE pf_diversions (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT        NOT NULL REFERENCES employees (id),
    diversion_type      VARCHAR(30)   NOT NULL,
    total_amount        NUMERIC(12,2) NOT NULL,
    emp_bucket_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    er_bucket_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    vpf_bucket_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    linked_loan_id      BIGINT REFERENCES employee_loans (id),
    sanction_date       DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_pf_diversions_type CHECK (diversion_type IN ('REFUNDABLE_LOAN', 'NON_REFUNDABLE_WITHDRAWAL')),
    CONSTRAINT ck_pf_diversions_status CHECK (status IN ('ACTIVE', 'SETTLED')),
    CONSTRAINT ck_pf_diversions_bucket_sum
        CHECK (emp_bucket_amount + er_bucket_amount + vpf_bucket_amount = total_amount)
);

CREATE INDEX ix_pf_diversions_employee_id ON pf_diversions (employee_id);
