-- Payroll Masters II: NPS contribution-range narrowing, the address-based Company Accommodation
-- rework, Vehicle Allotments, and the Statutory Parameters console. As with V66/V67, most of this was
-- already created/altered directly against the shared dev database by other tooling before this
-- migration existed - it reproduces that exact live shape (IF EXISTS/IF NOT EXISTS/ON CONFLICT) so it
-- is a no-op there and a real create+seed on a fresh environment.

-- NPS employee contribution range narrows from 10.00-100.00 (V66) to 3.00-10.00 per current policy.
ALTER TABLE employee_nps_declarations DROP CONSTRAINT IF EXISTS employee_nps_declarations_nps_percentage_check;
ALTER TABLE employee_nps_declarations ADD CONSTRAINT employee_nps_declarations_nps_percentage_check
    CHECK (nps_percentage >= 3.00 AND nps_percentage <= 10.00);

-- Backs the Employment tab's "Official Vehicle Provided" toggle - suppresses Transport Allowance
-- (Head 10) while true, mirroring how an active company-accommodation occupancy suppresses HRA.
ALTER TABLE employees ADD COLUMN IF NOT EXISTS has_office_car BOOLEAN NOT NULL DEFAULT FALSE;

-- Company Accommodation reworked from a quarter/estate model (V67) to an address-based one - JCI
-- does not own residential quarters, accommodation is leased or company-provided at an address, not
-- assigned a quarter number in an estate.
ALTER TABLE employee_quarter_allotments DROP COLUMN IF EXISTS quarter_no;
ALTER TABLE employee_quarter_allotments DROP COLUMN IF EXISTS quarter_type;
ALTER TABLE employee_quarter_allotments DROP COLUMN IF EXISTS estate_location;
ALTER TABLE employee_quarter_allotments ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE employee_quarter_allotments ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(255);
ALTER TABLE employee_quarter_allotments ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE employee_quarter_allotments ADD COLUMN IF NOT EXISTS state_code VARCHAR(20);
ALTER TABLE employee_quarter_allotments ADD COLUMN IF NOT EXISTS pincode VARCHAR(10);
ALTER TABLE employee_quarter_allotments ADD COLUMN IF NOT EXISTS sync_current_address BOOLEAN NOT NULL DEFAULT TRUE;

DO $$ BEGIN
    ALTER TABLE employee_quarter_allotments
        ADD CONSTRAINT employee_quarter_allotments_state_code_fkey FOREIGN KEY (state_code) REFERENCES state_master (state_code);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_quarter_status ON employee_quarter_allotments (status);

CREATE TABLE IF NOT EXISTS employee_vehicle_allotments (
    id                        BIGSERIAL     PRIMARY KEY,
    employee_id               BIGINT        NOT NULL REFERENCES employees (id) ON DELETE RESTRICT,
    allotment_order_no        VARCHAR(100),
    vehicle_reg_no            VARCHAR(50)   NOT NULL,
    vehicle_make_model        VARCHAR(100),
    driver_provided           BOOLEAN       NOT NULL DEFAULT TRUE,
    personal_use_allowed      BOOLEAN       NOT NULL DEFAULT TRUE,
    deduction_applicable      BOOLEAN       NOT NULL DEFAULT TRUE,
    monthly_deduction_amount  NUMERIC(10,2) NOT NULL DEFAULT 2000.00 CHECK (monthly_deduction_amount >= 0.00),
    allotted_from             DATE          NOT NULL,
    surrendered_on            DATE,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SURRENDERED', 'TRANSFERRED', 'CANCELLED')),
    remarks                   VARCHAR(255),
    created_at                TIMESTAMPTZ   DEFAULT now(),
    updated_at                TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT chk_vehicle_allotment_dates CHECK (surrendered_on IS NULL OR surrendered_on >= allotted_from)
);
CREATE INDEX IF NOT EXISTS idx_vehicle_emp_dates ON employee_vehicle_allotments (employee_id, allotted_from, surrendered_on);
CREATE INDEX IF NOT EXISTS idx_vehicle_status ON employee_vehicle_allotments (status);

CREATE TABLE IF NOT EXISTS payroll_statutory_parameters (
    id              BIGSERIAL     PRIMARY KEY,
    param_key       VARCHAR(50)   NOT NULL,
    param_name      VARCHAR(150)  NOT NULL,
    param_value     NUMERIC(12,4) NOT NULL,
    val_type        VARCHAR(20)   NOT NULL DEFAULT 'DECIMAL' CHECK (val_type IN ('DECIMAL', 'PERCENTAGE', 'AMOUNT')),
    effective_from  DATE          NOT NULL DEFAULT '2020-04-01',
    effective_to    DATE,
    remarks         VARCHAR(255),
    updated_at      TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT uq_payroll_param_key_date UNIQUE (param_key, effective_from)
);
CREATE INDEX IF NOT EXISTS idx_payroll_param_lookup ON payroll_statutory_parameters (param_key, effective_from);

-- Real current JCI statutory parameter values - matches what is already live exactly.
INSERT INTO payroll_statutory_parameters (param_key, param_name, param_value, val_type, effective_from, remarks) VALUES
    ('CPF_EMP_RATE', 'Employee CPF Contribution Rate', 12.0000, 'PERCENTAGE', '2020-04-01', 'Standard CPSE Employee PF Rate (12% of Basic + DA)'),
    ('EPS_WAGE_CEILING', 'Statutory EPS Wage Ceiling', 15000.0000, 'AMOUNT', '2014-09-01', 'Statutory wage ceiling for standard EPS (₹15,000)'),
    ('EPS_BASE_RATE', 'EPS Contribution Base Rate', 8.3300, 'PERCENTAGE', '2020-04-01', '8.33% allocated to Pension Fund up to ceiling'),
    ('EPS_HIGHER_EXTRA_RATE', 'Higher EPS Contribution Rate on Balance', 1.1600, 'PERCENTAGE', '2023-05-03', '1.16% on PF wages exceeding ₹15,000 per SC EPS-95 judgment'),
    ('NPS_EMPLOYER_RATE', 'Employer NPS Contribution Rate', 10.0000, 'PERCENTAGE', '2020-04-01', 'Employer NPS matching rate (10% of Basic + DA)'),
    ('GIS_FLAT_AMOUNT', 'Group Insurance Scheme (GIS) Flat Deduction', 1.0000, 'AMOUNT', '2020-04-01', 'Standard non-executive flat GIS deduction (Head 45)'),
    ('DIRECTOR_CAR_USE_DEDUCTION', 'Director Official Car Personal Usage Recovery', 2000.0000, 'AMOUNT', '2020-04-01', 'Standard monthly recovery for personal use of office car (Head 44)'),
    ('LWF_WB_AMOUNT', 'West Bengal Labour Welfare Fund Deduction', 3.0000, 'AMOUNT', '2020-04-01', 'Deducted semi-annually in June and December for Non-Executives in WB')
ON CONFLICT ON CONSTRAINT uq_payroll_param_key_date DO NOTHING;
