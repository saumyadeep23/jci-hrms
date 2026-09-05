CREATE TABLE staging_legacy_leave_balances (
    id                BIGSERIAL PRIMARY KEY,
    employee_code     VARCHAR(50)  NOT NULL,
    leave_type_code   VARCHAR(20)  NOT NULL,
    opening_balance   NUMERIC(4,1) NOT NULL,
    as_on_date        DATE         NOT NULL,
    status            VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_staging_leave_balances_status CHECK (status IN ('PENDING', 'PROMOTED', 'REJECTED'))
);

CREATE TABLE staging_legacy_loans (
    id                      BIGSERIAL PRIMARY KEY,
    employee_code           VARCHAR(50)   NOT NULL,
    loan_type_code          VARCHAR(20)   NOT NULL,
    loan_account_number     VARCHAR(50)   NOT NULL,
    principal_amount        NUMERIC(12,2) NOT NULL,
    interest_rate           NUMERIC(5,2)  NOT NULL,
    sanction_date           DATE          NOT NULL,
    disbursement_date       DATE,
    tenure_months           INTEGER       NOT NULL,
    outstanding_principal   NUMERIC(12,2),
    remaining_installments  INTEGER,
    loan_status             VARCHAR(20)   NOT NULL,
    status                  VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    rejection_reason        TEXT,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_staging_loans_loan_status CHECK (loan_status IN ('ACTIVE', 'CLOSED', 'FORECLOSED', 'WRITTEN_OFF')),
    CONSTRAINT ck_staging_loans_status CHECK (status IN ('PENDING', 'PROMOTED', 'REJECTED'))
);

CREATE TABLE staging_legacy_loan_transactions (
    id                    BIGSERIAL PRIMARY KEY,
    staging_loan_id       BIGINT        NOT NULL REFERENCES staging_legacy_loans (id),
    loan_account_number   VARCHAR(50)   NOT NULL,
    payment_date          DATE          NOT NULL,
    principal_component   NUMERIC(12,2) NOT NULL,
    interest_component    NUMERIC(12,2) NOT NULL,
    total_amount           NUMERIC(12,2) NOT NULL,
    status                 VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    rejection_reason       TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_staging_loan_transactions_status CHECK (status IN ('PENDING', 'PROMOTED', 'REJECTED'))
);

CREATE INDEX ix_staging_loan_transactions_staging_loan_id ON staging_legacy_loan_transactions (staging_loan_id);

CREATE TABLE staging_legacy_salary_months (
    id                 BIGSERIAL PRIMARY KEY,
    employee_code      VARCHAR(50)   NOT NULL,
    salary_year        INTEGER       NOT NULL,
    salary_month       INTEGER       NOT NULL,
    basic_pay          NUMERIC(12,2) NOT NULL,
    gross_earnings     NUMERIC(12,2) NOT NULL,
    total_deductions   NUMERIC(12,2) NOT NULL,
    net_pay            NUMERIC(12,2) NOT NULL,
    status             VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    rejection_reason   TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_staging_salary_months_status CHECK (status IN ('PENDING', 'PROMOTED', 'REJECTED')),
    CONSTRAINT ck_staging_salary_months_month CHECK (salary_month BETWEEN 1 AND 12)
);

CREATE TABLE staging_legacy_salary_heads (
    id                        BIGSERIAL PRIMARY KEY,
    staging_salary_month_id   BIGINT        NOT NULL REFERENCES staging_legacy_salary_months (id),
    salary_head_code          VARCHAR(20)   NOT NULL,
    head_category              VARCHAR(20)   NOT NULL,
    amount                     NUMERIC(12,2) NOT NULL,
    status                     VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    rejection_reason           TEXT,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_staging_salary_heads_category CHECK (head_category IN ('EARNING', 'EMPLOYEE_DEDUCTION')),
    CONSTRAINT ck_staging_salary_heads_status CHECK (status IN ('PENDING', 'PROMOTED', 'REJECTED'))
);

CREATE INDEX ix_staging_salary_heads_staging_salary_month_id ON staging_legacy_salary_heads (staging_salary_month_id);

CREATE TABLE staging_legacy_service_book (
    id                BIGSERIAL PRIMARY KEY,
    employee_code     VARCHAR(50)  NOT NULL,
    event_date        DATE         NOT NULL,
    event_type        VARCHAR(50)  NOT NULL,
    order_number      VARCHAR(100),
    order_date        DATE,
    from_designation  VARCHAR(100),
    to_designation    VARCHAR(100),
    from_department   VARCHAR(100),
    to_department     VARCHAR(100),
    from_ro           VARCHAR(150),
    to_ro             VARCHAR(150),
    basic_pay         NUMERIC(12,2),
    description       TEXT,
    status            VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_staging_service_book_status CHECK (status IN ('PENDING', 'PROMOTED', 'REJECTED'))
);

CREATE TABLE employee_service_book (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT        NOT NULL REFERENCES employees (id),
    event_date          DATE          NOT NULL,
    event_type          VARCHAR(50)   NOT NULL,
    order_number        VARCHAR(100),
    order_date          DATE,
    department_id       BIGINT REFERENCES departments (id),
    designation_id      BIGINT REFERENCES designations (id),
    regional_office_id  BIGINT REFERENCES ro_master (id),
    pay_scale_id        BIGINT REFERENCES pay_scale_master (id),
    basic_pay           NUMERIC(12,2),
    event_description   TEXT,
    remarks             TEXT,
    is_migrated         BOOLEAN       NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX ix_employee_service_book_employee_id ON employee_service_book (employee_id);
CREATE INDEX ix_employee_service_book_event_date ON employee_service_book (event_date);

-- Legacy migration needs to represent two loan/repayment concepts the live
-- system never produces on its own: a loan that was administratively
-- written off (not foreclosed, not paying to zero), and a repayment row
-- sourced from historical import rather than payroll/cash/foreclosure.
ALTER TABLE employee_loans DROP CONSTRAINT ck_employee_loans_status;
ALTER TABLE employee_loans ADD CONSTRAINT ck_employee_loans_status
    CHECK (status IN ('SANCTIONED', 'DISBURSED', 'ACTIVE', 'CLOSED', 'FORECLOSED', 'WRITTEN_OFF'));

ALTER TABLE loan_repayments DROP CONSTRAINT ck_loan_repayments_source;
ALTER TABLE loan_repayments ADD CONSTRAINT ck_loan_repayments_source
    CHECK (repayment_source IN ('PAYROLL_DEDUCTION', 'CASH_DEPOSIT', 'FORECLOSURE', 'LEGACY_IMPORT'));

-- FR-MIG.8: a promoted legacy salary month becomes a real, ordinary
-- payroll_runs/payslips/payslip_items row set (not a separate schema) so
-- v_unified_salary_head_history below is a simple join, not a UNION across
-- two different data shapes. run_type/is_migrated distinguish origin.
ALTER TABLE payroll_runs ADD COLUMN run_type VARCHAR(20) NOT NULL DEFAULT 'LIVE';
ALTER TABLE payroll_runs ADD COLUMN is_migrated BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE payroll_runs ADD CONSTRAINT ck_payroll_runs_run_type CHECK (run_type IN ('LIVE', 'HISTORIC_MIGRATED'));

CREATE VIEW v_unified_salary_head_history AS
SELECT
    pi.id       AS payslip_item_id,
    pr.id       AS payroll_run_id,
    pr.cycle_year,
    pr.cycle_month,
    pr.run_type,
    pr.is_migrated,
    ps.id       AS payslip_id,
    ps.employee_id,
    shm.id      AS salary_head_id,
    shm.code    AS salary_head_code,
    shm.name    AS salary_head_name,
    shm.head_type,
    pi.amount,
    pi.cause_remarks
FROM payslip_items pi
JOIN payslips ps ON ps.id = pi.payslip_id
JOIN payroll_runs pr ON pr.id = ps.payroll_run_id
JOIN salary_head_master shm ON shm.id = pi.salary_head_id;
