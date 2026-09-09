-- Payroll Computation Engine: the batch/monthly-record schema (payroll_batches,
-- payroll_monthly_records, payroll_monthly_head_items, payroll_monthly_statutory_items,
-- payroll_monthly_adjustments), the Section 192 TDS engine's tables (payroll_tax_slabs,
-- employee_tax_regimes, payroll_tax_overrides) and its view_employee_tax_ytd_aggregates view, and two
-- new accommodation-recovery salary heads were all already created directly against the shared dev
-- database by other tooling before this migration existed (same situation as V66/67/68 - see their
-- own comments). Reproduces that exact live shape so it's a no-op there and a real create+seed on a
-- fresh environment.

CREATE TABLE IF NOT EXISTS payroll_batches (
    id                   BIGSERIAL     PRIMARY KEY,
    batch_no             VARCHAR(50)   NOT NULL UNIQUE,
    sal_month            INT           NOT NULL CHECK (sal_month >= 1 AND sal_month <= 12),
    sal_year             INT           NOT NULL CHECK (sal_year >= 2000),
    financial_year       VARCHAR(10)   NOT NULL,
    status               VARCHAR(30)   NOT NULL DEFAULT 'DRAFT'
                             CHECK (status IN ('DRAFT', 'HR_FINALIZED', 'FINANCE_APPROVED', 'REJECTED_TO_HR', 'DISBURSED', 'CANCELLED')),
    total_employees      INT           NOT NULL DEFAULT 0,
    total_gross          NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_deductions     NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_net            NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    hr_finalized_by      BIGINT        REFERENCES employees (id),
    hr_finalized_at      TIMESTAMPTZ,
    hr_remarks           TEXT,
    finance_approved_by  BIGINT        REFERENCES employees (id),
    finance_approved_at  TIMESTAMPTZ,
    finance_remarks      TEXT,
    voucher_ref_no       VARCHAR(50),
    created_at           TIMESTAMPTZ   DEFAULT now(),
    updated_at           TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT uq_payroll_batch_month_year UNIQUE (sal_month, sal_year)
);

CREATE TABLE IF NOT EXISTS payroll_monthly_records (
    tran_id           BIGSERIAL     PRIMARY KEY,
    batch_id          BIGINT        NOT NULL REFERENCES payroll_batches (id) ON DELETE CASCADE,
    employee_id       BIGINT        NOT NULL REFERENCES employees (id),
    emp_code          VARCHAR(20)   NOT NULL,
    month             INT           NOT NULL CHECK (month >= 1 AND month <= 12),
    year              INT           NOT NULL CHECK (year >= 2000),
    loc_code          VARCHAR(20),
    desgn_code        VARCHAR(20),
    city_class        VARCHAR(5),
    pay_pattern       VARCHAR(10)   DEFAULT 'IDA',
    days_in_month     INT           NOT NULL,
    days_present      NUMERIC(4,1)  NOT NULL DEFAULT 0.0,
    days_lop          NUMERIC(4,1)  NOT NULL DEFAULT 0.0,
    basic_pay         NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    gross_amount      NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_deductions  NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    net_amount        NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    is_salary_held    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT uq_payroll_record_batch_emp UNIQUE (batch_id, employee_id)
);

CREATE TABLE IF NOT EXISTS payroll_monthly_head_items (
    id          BIGSERIAL     PRIMARY KEY,
    tran_id     BIGINT        NOT NULL REFERENCES payroll_monthly_records (tran_id) ON DELETE CASCADE,
    head_count  INT           NOT NULL REFERENCES payroll_salary_heads (head_count),
    amount      NUMERIC(12,2) NOT NULL,

    CONSTRAINT uq_payroll_tran_sal_head UNIQUE (tran_id, head_count)
);

CREATE TABLE IF NOT EXISTS payroll_monthly_statutory_items (
    id               BIGSERIAL     PRIMARY KEY,
    tran_id          BIGINT        NOT NULL REFERENCES payroll_monthly_records (tran_id) ON DELETE CASCADE,
    stat_head_count  INT           NOT NULL REFERENCES payroll_statutory_heads (stat_head_count),
    amount           NUMERIC(12,2) NOT NULL,

    CONSTRAINT uq_payroll_tran_stat_head UNIQUE (tran_id, stat_head_count)
);
CREATE INDEX IF NOT EXISTS idx_payroll_statutory_items_tran ON payroll_monthly_statutory_items (tran_id);

CREATE TABLE IF NOT EXISTS payroll_monthly_adjustments (
    id                  BIGSERIAL     PRIMARY KEY,
    batch_id            BIGINT        NOT NULL REFERENCES payroll_batches (id) ON DELETE CASCADE,
    employee_id         BIGINT        NOT NULL REFERENCES employees (id),
    head_count          INT           NOT NULL REFERENCES payroll_salary_heads (head_count),
    amount              NUMERIC(10,2) NOT NULL,
    cause_description   VARCHAR(255),
    entered_by          BIGINT        REFERENCES employees (id),
    created_at          TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT uq_payroll_emp_batch_head UNIQUE (batch_id, employee_id, head_count)
);

INSERT INTO payroll_salary_heads (head_count, description, short_name, effect_type, is_variable, applicable_for, sal_slip_vis, basic_dependent) VALUES
    (63, 'Company Accommodation - License Fee Recovery', 'ACCOM_LICENSE_FEE', 'DEDUCTION', TRUE, 'BOTH', NULL, FALSE),
    (64, 'Company Accommodation - Water Charges Recovery', 'ACCOM_WATER', 'DEDUCTION', TRUE, 'BOTH', NULL, FALSE),
    (65, 'Company Accommodation - Electric Charges Recovery', 'ACCOM_ELECTRIC', 'DEDUCTION', TRUE, 'BOTH', NULL, FALSE)
ON CONFLICT (head_count) DO NOTHING;

-- Section 192 TDS engine's slab table - already live for FY2025-2026/2026-2027 (Budget 2025 New Regime
-- slabs, unchanged Old Regime slabs); reproduced here so a fresh environment has them too.
CREATE TABLE IF NOT EXISTS payroll_tax_slabs (
    id              BIGSERIAL     PRIMARY KEY,
    financial_year  VARCHAR(10)   NOT NULL,
    regime          VARCHAR(10)   NOT NULL CHECK (regime IN ('NEW', 'OLD')),
    slab_min        NUMERIC(12,2) NOT NULL CHECK (slab_min >= 0.00),
    slab_max        NUMERIC(12,2),
    tax_rate        NUMERIC(5,2)  NOT NULL CHECK (tax_rate >= 0.00 AND tax_rate <= 100.00),
    cess_rate       NUMERIC(5,2)  NOT NULL DEFAULT 4.00 CHECK (cess_rate >= 0.00),
    created_at      TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_tax_slab_entry UNIQUE (financial_year, regime, slab_min),
    CONSTRAINT payroll_tax_slabs_check CHECK (slab_max IS NULL OR slab_max > slab_min)
);
CREATE INDEX IF NOT EXISTS idx_tax_slabs_lookup ON payroll_tax_slabs (financial_year, regime, slab_min);

INSERT INTO payroll_tax_slabs (financial_year, regime, slab_min, slab_max, tax_rate, cess_rate) VALUES
    ('2025-2026', 'NEW', 0.00, 300000.00, 0.00, 4.00),
    ('2025-2026', 'NEW', 300000.01, 700000.00, 5.00, 4.00),
    ('2025-2026', 'NEW', 700000.01, 1000000.00, 10.00, 4.00),
    ('2025-2026', 'NEW', 1000000.01, 1200000.00, 15.00, 4.00),
    ('2025-2026', 'NEW', 1200000.01, 1500000.00, 20.00, 4.00),
    ('2025-2026', 'NEW', 1500000.01, NULL, 30.00, 4.00),
    ('2025-2026', 'OLD', 0.00, 250000.00, 0.00, 4.00),
    ('2025-2026', 'OLD', 250000.01, 500000.00, 5.00, 4.00),
    ('2025-2026', 'OLD', 500000.01, 1000000.00, 20.00, 4.00),
    ('2025-2026', 'OLD', 1000000.01, NULL, 30.00, 4.00),
    ('2026-2027', 'NEW', 0.00, 300000.00, 0.00, 4.00),
    ('2026-2027', 'NEW', 300000.01, 700000.00, 5.00, 4.00),
    ('2026-2027', 'NEW', 700000.01, 1000000.00, 10.00, 4.00),
    ('2026-2027', 'NEW', 1000000.01, 1200000.00, 15.00, 4.00),
    ('2026-2027', 'NEW', 1200000.01, 1500000.00, 20.00, 4.00),
    ('2026-2027', 'NEW', 1500000.01, NULL, 30.00, 4.00),
    ('2026-2027', 'OLD', 0.00, 250000.00, 0.00, 4.00),
    ('2026-2027', 'OLD', 250000.01, 500000.00, 5.00, 4.00),
    ('2026-2027', 'OLD', 500000.01, 1000000.00, 20.00, 4.00),
    ('2026-2027', 'OLD', 1000000.01, NULL, 30.00, 4.00)
ON CONFLICT ON CONSTRAINT uq_tax_slab_entry DO NOTHING;

CREATE TABLE IF NOT EXISTS employee_tax_regimes (
    id              BIGSERIAL     PRIMARY KEY,
    employee_id     BIGINT        NOT NULL REFERENCES employees (id) ON DELETE RESTRICT,
    financial_year  VARCHAR(10)   NOT NULL,
    selected_regime VARCHAR(10)   NOT NULL DEFAULT 'NEW' CHECK (selected_regime IN ('NEW', 'OLD')),
    is_locked       BOOLEAN       NOT NULL DEFAULT FALSE,
    opted_at        TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP,
    opted_by        VARCHAR(100),
    remarks         VARCHAR(255),

    CONSTRAINT uq_emp_fy_regime UNIQUE (employee_id, financial_year)
);
CREATE INDEX IF NOT EXISTS idx_emp_tax_regime ON employee_tax_regimes (employee_id, financial_year);

CREATE TABLE IF NOT EXISTS payroll_tax_overrides (
    id                 BIGSERIAL     PRIMARY KEY,
    employee_id        BIGINT        NOT NULL REFERENCES employees (id) ON DELETE RESTRICT,
    financial_year     VARCHAR(10)   NOT NULL,
    payroll_month      INT           NOT NULL CHECK (payroll_month >= 1 AND payroll_month <= 12),
    payroll_year       INT           NOT NULL,
    calculated_amount  NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (calculated_amount >= 0.00),
    overridden_amount  NUMERIC(12,2) NOT NULL CHECK (overridden_amount >= 0.00),
    override_reason    VARCHAR(500)  NOT NULL,
    applied_by         VARCHAR(100)  NOT NULL,
    scope              VARCHAR(20)   NOT NULL DEFAULT 'MONTH_ONLY' CHECK (scope IN ('MONTH_ONLY', 'REMAINDER_OF_FY')),
    created_at         TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_emp_month_override UNIQUE (employee_id, payroll_year, payroll_month),
    CONSTRAINT chk_override_reason_minlen CHECK (char_length(TRIM(override_reason)) >= 10)
);
CREATE INDEX IF NOT EXISTS idx_tax_override_lookup ON payroll_tax_overrides (employee_id, financial_year, payroll_year, payroll_month);

-- YTD actuals per (employee, financial_year), sourced from already-persisted payroll_monthly_records
-- + payroll_monthly_head_items (head 40 = Income Tax/TDS, 27 = CPF, 49 = Professional Tax) - the TDS
-- engine's cumulative-projection Step D reads this every batch run.
DROP VIEW IF EXISTS view_employee_tax_ytd_aggregates;
CREATE OR REPLACE VIEW view_employee_tax_ytd_aggregates AS
SELECT
    employee_id,
    employee_code,
    full_name,
    pan_number,
    financial_year,
    COUNT(*)                    AS system_months_count,
    SUM(gross_amount)           AS total_cumulative_gross,
    SUM(tds_amount)             AS total_cumulative_tds_paid,
    SUM(cpf_amount)             AS total_cumulative_cpf,
    SUM(ptax_amount)            AS total_cumulative_ptax
FROM (
    SELECT
        pmr.tran_id,
        pmr.employee_id,
        e.employee_code,
        e.full_name,
        e.pan_number,
        CASE WHEN pmr.month >= 4 THEN pmr.year::text || '-' || (pmr.year + 1)::text
             ELSE (pmr.year - 1)::text || '-' || pmr.year::text END AS financial_year,
        pmr.gross_amount,
        COALESCE((SELECT amount FROM payroll_monthly_head_items WHERE tran_id = pmr.tran_id AND head_count = 40), 0) AS tds_amount,
        COALESCE((SELECT amount FROM payroll_monthly_head_items WHERE tran_id = pmr.tran_id AND head_count = 27), 0) AS cpf_amount,
        COALESCE((SELECT amount FROM payroll_monthly_head_items WHERE tran_id = pmr.tran_id AND head_count = 49), 0) AS ptax_amount
    FROM payroll_monthly_records pmr
    JOIN employees e ON e.id = pmr.employee_id
    WHERE pmr.is_salary_held = FALSE
) monthly
GROUP BY employee_id, employee_code, full_name, pan_number, financial_year;
