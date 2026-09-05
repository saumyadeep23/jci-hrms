CREATE TABLE salary_head_master (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    head_type   VARCHAR(25)  NOT NULL,
    gl_code     VARCHAR(20),
    is_variable BOOLEAN      NOT NULL DEFAULT false,
    is_taxable  BOOLEAN      NOT NULL DEFAULT true,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT ck_salary_head_master_head_type CHECK (head_type IN ('EARNING', 'DEDUCTION', 'EMPLOYER_CONTRIBUTION'))
);

CREATE UNIQUE INDEX uq_salary_head_master_code_active ON salary_head_master (code) WHERE deleted_at IS NULL;

-- Core heads referenced by PayrollComputationService.
INSERT INTO salary_head_master (code, name, head_type, is_variable, is_taxable) VALUES
    ('BASIC',  'Basic Pay',                          'EARNING',               false, true),
    ('DA',     'Dearness Allowance',                 'EARNING',               true,  true),
    ('HRA',    'House Rent Allowance',                'EARNING',               false, true),
    ('TA',     'Transport Allowance',                 'EARNING',               true,  true),
    ('EPF_EE', 'Provident Fund (Employee)',           'DEDUCTION',             false, false),
    ('EPF_ER', 'Provident Fund (Employer)',           'EMPLOYER_CONTRIBUTION', false, false),
    ('EPS_ER', 'Pension Scheme - EPS-95 (Employer)',  'EMPLOYER_CONTRIBUTION', false, false);

CREATE TABLE da_rate_history (
    id             BIGSERIAL PRIMARY KEY,
    scale_type     VARCHAR(3)   NOT NULL,
    effective_from DATE         NOT NULL,
    da_percentage  NUMERIC(5,2) NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT true,

    CONSTRAINT ck_da_rate_history_scale_type CHECK (scale_type IN ('IDA', 'CDA'))
);

CREATE INDEX ix_da_rate_history_scale_type_effective_from ON da_rate_history (scale_type, effective_from);

CREATE TABLE payroll_runs (
    id           BIGSERIAL PRIMARY KEY,
    cycle_year   INTEGER     NOT NULL,
    cycle_month  INTEGER     NOT NULL,
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    finalized_by VARCHAR(150),
    finalized_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_payroll_runs_cycle UNIQUE (cycle_year, cycle_month),
    CONSTRAINT ck_payroll_runs_status CHECK (status IN ('DRAFT', 'COMPUTED', 'FINALIZED')),
    CONSTRAINT ck_payroll_runs_cycle_month CHECK (cycle_month BETWEEN 1 AND 12)
);

CREATE TABLE payslips (
    id                      BIGSERIAL PRIMARY KEY,
    payroll_run_id          BIGINT        NOT NULL REFERENCES payroll_runs (id),
    employee_id             BIGINT        NOT NULL REFERENCES employees (id),
    basic_pay               NUMERIC(12,2) NOT NULL,
    total_earnings          NUMERIC(12,2) NOT NULL,
    total_deductions        NUMERIC(12,2) NOT NULL,
    employer_contributions  NUMERIC(12,2) NOT NULL,
    net_pay                 NUMERIC(12,2) NOT NULL,
    lop_days                NUMERIC(4,1)  NOT NULL DEFAULT 0,
    is_hold                 BOOLEAN       NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_payslips_run_employee UNIQUE (payroll_run_id, employee_id)
);

CREATE INDEX ix_payslips_payroll_run_id ON payslips (payroll_run_id);
CREATE INDEX ix_payslips_employee_id ON payslips (employee_id);

CREATE TABLE payslip_items (
    id             BIGSERIAL PRIMARY KEY,
    payslip_id     BIGINT        NOT NULL REFERENCES payslips (id),
    salary_head_id BIGINT        NOT NULL REFERENCES salary_head_master (id),
    amount         NUMERIC(12,2) NOT NULL,
    cause_remarks  VARCHAR(255)
);

CREATE INDEX ix_payslip_items_payslip_id ON payslip_items (payslip_id);
