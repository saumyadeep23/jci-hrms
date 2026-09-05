-- Feature: 4-Tier Employment Category governance (PIMS_SPEC.md Step 6).
CREATE TABLE IF NOT EXISTS vendor_master (
    id                         BIGSERIAL PRIMARY KEY,
    vendor_code                VARCHAR(30)  NOT NULL,
    vendor_name                VARCHAR(200) NOT NULL,
    trade_name                 VARCHAR(200),
    gstin                      VARCHAR(15),
    pan_number                 VARCHAR(10),
    epf_registration_no        VARCHAR(50),
    esic_registration_no       VARCHAR(50),
    contract_start_date        DATE         NOT NULL,
    contract_end_date          DATE         NOT NULL,
    contact_person             VARCHAR(150),
    contact_phone              VARCHAR(20),
    contact_email               VARCHAR(150),
    office_address              TEXT,
    service_charge_percentage   NUMERIC(5,2) DEFAULT 0.00,
    is_active                   BOOLEAN      NOT NULL DEFAULT true,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at                  TIMESTAMPTZ,

    CONSTRAINT vendor_master_vendor_code_key UNIQUE (vendor_code),
    CONSTRAINT vendor_master_gstin_key UNIQUE (gstin),
    CONSTRAINT chk_vendor_contract_dates CHECK (contract_end_date >= contract_start_date),
    CONSTRAINT vendor_master_gstin_check CHECK (gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$'),
    CONSTRAINT vendor_master_pan_number_check CHECK (pan_number IS NULL OR pan_number ~ '^[A-Z]{5}[0-9]{4}[A-Z]$')
);

CREATE INDEX IF NOT EXISTS idx_vendor_active ON vendor_master (is_active) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS employee_employment_categories (
    id                       BIGSERIAL PRIMARY KEY,
    employee_id              BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    employment_category      VARCHAR(30)  NOT NULL,
    pay_scale_id             BIGINT REFERENCES pay_scale_master (id) ON DELETE RESTRICT,
    regular_basic_pay        NUMERIC(12,2),
    regular_grade_pay        NUMERIC(10,2) DEFAULT 0.00,
    daily_wage_rate          NUMERIC(10,2),
    wage_revision_order_no    VARCHAR(100),
    fixed_lump_sum_monthly    NUMERIC(12,2),
    contract_start_date       DATE,
    contract_end_date         DATE,
    contract_ref_order        VARCHAR(100),
    vendor_id                 BIGINT REFERENCES vendor_master (id) ON DELETE RESTRICT,
    monthly_ctc                NUMERIC(12,2),
    billing_rate_monthly       NUMERIC(12,2),
    agency_employee_id         VARCHAR(50),
    is_active                  BOOLEAN      NOT NULL DEFAULT true,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at                 TIMESTAMPTZ,

    CONSTRAINT employee_employment_categories_employee_id_key UNIQUE (employee_id),
    CONSTRAINT employee_employment_categories_employment_category_check
        CHECK (employment_category IN ('REGULAR', 'CASUAL', 'CONTRACTUAL', 'OUTSOURCED')),
    CONSTRAINT chk_regular_data CHECK (employment_category <> 'REGULAR' OR (pay_scale_id IS NOT NULL AND regular_basic_pay IS NOT NULL)),
    CONSTRAINT chk_casual_data CHECK (employment_category <> 'CASUAL' OR daily_wage_rate IS NOT NULL),
    CONSTRAINT chk_contractual_data
        CHECK (employment_category <> 'CONTRACTUAL' OR (fixed_lump_sum_monthly IS NOT NULL AND contract_start_date IS NOT NULL)),
    CONSTRAINT chk_outsourced_data CHECK (employment_category <> 'OUTSOURCED' OR (vendor_id IS NOT NULL AND monthly_ctc IS NOT NULL)),
    CONSTRAINT employee_employment_categories_daily_wage_rate_check CHECK (daily_wage_rate IS NULL OR daily_wage_rate >= 0),
    CONSTRAINT employee_employment_categories_fixed_lump_sum_monthly_check CHECK (fixed_lump_sum_monthly IS NULL OR fixed_lump_sum_monthly >= 0),
    CONSTRAINT employee_employment_categories_monthly_ctc_check CHECK (monthly_ctc IS NULL OR monthly_ctc >= 0)
);

CREATE INDEX IF NOT EXISTS idx_emp_category ON employee_employment_categories (employment_category);
CREATE INDEX IF NOT EXISTS idx_emp_vendor_id ON employee_employment_categories (vendor_id);

CREATE TABLE IF NOT EXISTS outsourced_salary_breakdown (
    id           BIGSERIAL PRIMARY KEY,
    employee_id  BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    head_code    VARCHAR(20)  NOT NULL,
    head_name    VARCHAR(100) NOT NULL,
    head_type    VARCHAR(20)  NOT NULL,
    amount       NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    is_active    BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT outsourced_salary_breakdown_head_type_check
        CHECK (head_type IN ('EARNING', 'DEDUCTION', 'EMPLOYER_STATUTORY', 'VENDOR_FEE'))
);

CREATE INDEX IF NOT EXISTS idx_outsourced_salary_breakdown_employee_id ON outsourced_salary_breakdown (employee_id);
