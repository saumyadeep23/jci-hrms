-- JCI Payroll Engine master configuration tables: transport/procurement allowance rates, professional
-- tax slabs, the salary-head and statutory-head catalogs, and per-employee-per-FY NPS declarations.
--
-- Unlike every other migration in this file set, these six tables were already created directly
-- against the shared dev database by other tooling before this migration existed (same situation as
-- V19's state_master/district_master) - complete with 42 transport-allowance rows, 23 P-Tax slabs,
-- the full 61-row salary-head catalog and 15-row statutory-head catalog. This migration reproduces
-- that exact live shape (CREATE TABLE IF NOT EXISTS, ON CONFLICT DO NOTHING seeding) so it is a no-op
-- there and a real create+seed on a fresh environment, rather than assuming a shape and colliding
-- with what is already live.
CREATE TABLE IF NOT EXISTS payroll_transport_allowance_rates (
    id              BIGSERIAL     PRIMARY KEY,
    grade_scale_id  BIGINT        REFERENCES grade_scale_master (id) ON DELETE RESTRICT,
    city_class      VARCHAR(5)    NOT NULL CHECK (city_class IN ('X', 'Y', 'Z')),
    base_rate       NUMERIC(10,2) NOT NULL CHECK (base_rate >= 0),
    effective_from  DATE          NOT NULL DEFAULT '2020-04-01',
    created_at      TIMESTAMPTZ   DEFAULT now()
);

-- Not part of the original live shape - added so PayrollMasterServiceImpl can't silently create two
-- rates for the same grade/city/date; the 42 existing rows have no such duplicates so this applies cleanly.
DO $$ BEGIN
    ALTER TABLE payroll_transport_allowance_rates
        ADD CONSTRAINT uq_transport_allowance_rate UNIQUE (grade_scale_id, city_class, effective_from);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS payroll_procurement_allowance_rates (
    id                  BIGSERIAL     PRIMARY KEY,
    designation_id      BIGINT        NOT NULL REFERENCES designations (id) ON DELETE RESTRICT,
    monthly_allowance   NUMERIC(10,2) NOT NULL CHECK (monthly_allowance >= 0),
    effective_from      DATE          NOT NULL DEFAULT '2020-04-01',
    effective_to        DATE,
    created_at          TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT uq_procurement_designation_date UNIQUE (designation_id, effective_from)
);
CREATE INDEX IF NOT EXISTS idx_proc_allow_desig ON payroll_procurement_allowance_rates (designation_id);

CREATE TABLE IF NOT EXISTS payroll_ptax_slabs (
    id                  BIGSERIAL     PRIMARY KEY,
    -- References state_master(state_code), a UNIQUE (not primary) key - see V19 for why state_master
    -- itself keeps a UUID PK; this table only ever needs the human-meaningful code.
    state_code          VARCHAR(20)   NOT NULL REFERENCES state_master (state_code) ON DELETE RESTRICT,
    slab_min            NUMERIC(12,2) NOT NULL,
    slab_max            NUMERIC(12,2),
    tax_amount          NUMERIC(8,2)  NOT NULL CHECK (tax_amount >= 0),
    -- Peak-surcharge month (e.g. February for WB, March for Assam/Odisha/Bihar) - null where a state
    -- has no such special month.
    special_month       INT CHECK (special_month IS NULL OR special_month BETWEEN 1 AND 12),
    special_month_tax   NUMERIC(8,2) CHECK (special_month_tax IS NULL OR special_month_tax >= 0),
    effective_from      DATE          NOT NULL DEFAULT '2020-04-01',
    effective_to        DATE,
    created_at          TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT chk_ptax_slab_order CHECK (slab_max IS NULL OR slab_max > slab_min),
    CONSTRAINT uq_ptax_state_slab UNIQUE (state_code, slab_min, effective_from)
);
CREATE INDEX IF NOT EXISTS idx_ptax_state_slab ON payroll_ptax_slabs (state_code, slab_min);

-- head_count is the catalog's own PK (not a surrogate id) - payroll_monthly_head_items and
-- payroll_monthly_adjustments (the not-yet-built monthly payroll run tables) already FK to it
-- directly, so it is a stable business key, not a display-order field.
CREATE TABLE IF NOT EXISTS payroll_salary_heads (
    head_count      INT          PRIMARY KEY,
    description     VARCHAR(150) NOT NULL,
    short_name      VARCHAR(50)  NOT NULL UNIQUE,
    effect_type     VARCHAR(20)  NOT NULL CHECK (effect_type IN ('EARNING', 'DEDUCTION', 'NO_EFFECT')),
    is_variable     BOOLEAN      NOT NULL DEFAULT FALSE,
    applicable_for  VARCHAR(20)  NOT NULL DEFAULT 'REGULAR',
    sal_slip_vis    INT,
    basic_dependent BOOLEAN      NOT NULL DEFAULT FALSE
);

-- The live catalog has no GL account code column yet - PayrollMasterController's salary-heads update
-- endpoint needs one (see task spec's "editable: ref_account_code, sal_slip_vis, is_variable"), so it
-- is added here rather than assumed already present. NULL at seed time throughout, same fill-in-later
-- placeholder pattern as grade_scale_master's contractual_lumpsum/outsourced_ctc - an admin sets each
-- head's real GL code via the Salary Heads Directory screen.
ALTER TABLE payroll_salary_heads ADD COLUMN IF NOT EXISTS ref_account_code VARCHAR(30);

CREATE TABLE IF NOT EXISTS payroll_statutory_heads (
    stat_head_count      INT          PRIMARY KEY,
    stat_head_descr      VARCHAR(150) NOT NULL,
    stat_head_short_name VARCHAR(50)  NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS employee_nps_declarations (
    id              BIGSERIAL    PRIMARY KEY,
    employee_id     BIGINT       NOT NULL REFERENCES employees (id),
    -- "2026-2027"-style April-March financial year label - see NpsDeclarationServiceImpl.currentFinancialYear().
    financial_year  VARCHAR(9)   NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    nps_percentage  NUMERIC(5,2) NOT NULL CHECK (nps_percentage BETWEEN 10.00 AND 100.00),
    effective_from  DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'CANCELLED')),
    declared_by     BIGINT       REFERENCES employees (id),
    remarks         VARCHAR(255),
    created_at      TIMESTAMPTZ  DEFAULT now(),

    CONSTRAINT uq_emp_nps_fy UNIQUE (employee_id, financial_year)
);

-- Seed data below mirrors exactly what is already live on the shared dev database - real JCI salary
-- and statutory head catalogs, real transport-allowance rates by grade scale and city class, and real
-- P-Tax slabs for the states JCI already operates in (WB, AS, BH, OR, AP, Tripura). ON CONFLICT DO
-- NOTHING against each table's natural key makes this a no-op there and a real seed on a fresh
-- environment.
INSERT INTO payroll_salary_heads (head_count, description, short_name, effect_type, is_variable, applicable_for, sal_slip_vis, basic_dependent) VALUES
    (1, 'Basic', 'BASIC', 'EARNING', TRUE, 'REGULAR', 1, FALSE),
    (2, 'No. of Days Present', 'DAYS_PRESENT', 'NO_EFFECT', TRUE, 'CASUAL', 1, FALSE),
    (3, 'Daily Wages for Casual Employees', 'CASL_WAGES', 'EARNING', FALSE, 'CASUAL', 2, FALSE),
    (4, 'Field Allowance for Casual Employees', 'CASL_FA', 'EARNING', FALSE, 'CASUAL', 3, FALSE),
    (5, 'Personal Pay', 'PP', 'EARNING', TRUE, 'REGULAR', 2, FALSE),
    (6, 'Special pay', 'SPL_PAY', 'EARNING', TRUE, 'REGULAR', 3, FALSE),
    (7, 'Central Dearness Allowance', 'CDA', 'EARNING', FALSE, 'REGULAR', 4, TRUE),
    (8, 'Industrial Dearness Allowance', 'IDA', 'EARNING', FALSE, 'REGULAR', 5, TRUE),
    (9, 'House Rent Allowance', 'HRA', 'EARNING', FALSE, 'REGULAR', 6, TRUE),
    (10, 'Transport Allowance', 'TRANS_ALLOW', 'EARNING', FALSE, 'REGULAR', 17, FALSE),
    (11, 'Machine Allowance', 'MC_ALLOW', 'EARNING', TRUE, 'REGULAR', 8, FALSE),
    (12, 'Washing Allowance', 'WASH_ALLOW', 'EARNING', FALSE, 'REGULAR', 9, FALSE),
    (13, 'Over Time', 'OT', 'EARNING', TRUE, 'REGULAR', 10, FALSE),
    (14, 'Arrear DA', 'ARR_DA', 'EARNING', FALSE, 'BOTH', 11, FALSE),
    (15, 'Shift Allowance', 'SHIFTG_ALLOW', 'EARNING', TRUE, 'REGULAR', 12, FALSE),
    (16, 'Procurement Allowance', 'PROC_ALLOW', 'EARNING', FALSE, 'REGULAR', 13, FALSE),
    (17, 'Adjustment Earning', 'ADJ_EARN', 'EARNING', TRUE, 'REGULAR', 14, FALSE),
    (18, 'Special Allowance/Charge Allowance', 'SPL/CHG ALLOW', 'EARNING', TRUE, 'REGULAR', 16, FALSE),
    (19, 'Tea Allowance', 'TEA_ALLOW', 'EARNING', FALSE, 'REGULAR', 18, FALSE),
    (20, 'Earned Leave Encashment Amount', 'ENCASH_AMT', 'EARNING', FALSE, 'REGULAR', 19, FALSE),
    (21, 'Remote Area Allowance', 'RMT_AREA_ALLOW', 'EARNING', FALSE, 'REGULAR', 7, TRUE),
    (22, 'Children Edu. Allow.', 'CEA', 'EARNING', TRUE, 'REGULAR', 21, FALSE),
    (23, 'Special Earning', 'SPL_EARN_1', 'EARNING', TRUE, 'BOTH', 22, FALSE),
    (24, 'Arrear TA', 'ARR_TA', 'EARNING', FALSE, 'REGULAR', 15, FALSE),
    (25, 'Special Earning Cause', 'SPL_EARN_CAUSE1', 'NO_EFFECT', FALSE, 'BOTH', NULL, FALSE),
    (27, 'Contributory Provident Fund', 'CPF', 'DEDUCTION', FALSE, 'BOTH', 23, TRUE),
    (28, 'Voluntary Provident Fund', 'VPF', 'DEDUCTION', FALSE, 'BOTH', 24, FALSE),
    (29, 'Arrear Contributory Provident Fund', 'ARR_CPF', 'DEDUCTION', FALSE, 'BOTH', NULL, FALSE),
    (30, 'CPF Loan Principle Repayment Installment', 'CPFLOAN_PRIN', 'DEDUCTION', FALSE, 'BOTH', 26, FALSE),
    (31, 'CPF Loan Interest Repayment Installment', 'CPFLOAN_INT', 'DEDUCTION', FALSE, 'BOTH', 27, FALSE),
    (32, 'Cumulative Time Deposit', 'CTD', 'DEDUCTION', TRUE, 'REGULAR', 29, FALSE),
    (33, 'LIC Policy Premium (Salary savings)', 'LIC_PRIM', 'DEDUCTION', FALSE, 'REGULAR', 30, FALSE),
    (34, 'Postal Life Insurance', 'PLI', 'DEDUCTION', TRUE, 'REGULAR', 31, FALSE),
    (35, 'Postal Recurring Deposit', 'REC_DEP', 'DEDUCTION', TRUE, 'REGULAR', 32, FALSE),
    (36, 'House Building Loan Principle Repayment Installment', 'HBLOAN_PRIN', 'DEDUCTION', TRUE, 'REGULAR', 34, FALSE),
    (37, 'House Building Loan Interest Repayment Installment', 'HBLOAN_INT', 'DEDUCTION', TRUE, 'REGULAR', 35, FALSE),
    (38, 'Festival Advance Loan Principle Repayment Installment', 'FEST_ADV', 'DEDUCTION', FALSE, 'BOTH', 36, FALSE),
    (39, 'Flood Advance Loan Principle Repayment Installment', 'FLOOD_ADV', 'DEDUCTION', FALSE, 'REGULAR', 37, FALSE),
    (40, 'Income Tax', 'I_TAX', 'DEDUCTION', FALSE, 'REGULAR', 38, FALSE),
    (41, 'Union Subscription', 'UNION_SUBS', 'DEDUCTION', FALSE, 'REGULAR', 39, FALSE),
    (42, 'Recreation Club', 'REC_CLUB', 'DEDUCTION', FALSE, 'REGULAR', 40, FALSE),
    (43, 'Labour Welfare Fund', 'LWF', 'DEDUCTION', FALSE, 'REGULAR', 41, FALSE),
    (44, 'Car Use Charges', 'CAR_USE', 'DEDUCTION', TRUE, 'REGULAR', 42, FALSE),
    (45, 'Group Insurance', 'GROUP_INS', 'DEDUCTION', FALSE, 'REGULAR', 43, FALSE),
    (46, 'Co-operative Loan', 'CO_OPERATIVE', 'DEDUCTION', TRUE, 'BOTH', 49, FALSE),
    (47, 'Thrift Fund', 'THRIFT_FUND', 'DEDUCTION', FALSE, 'REGULAR', 44, FALSE),
    (48, 'Adjustment Deduction', 'ADJ_DEDN', 'DEDUCTION', TRUE, 'REGULAR', 50, FALSE),
    (49, 'Professional Tax', 'P_TAX', 'DEDUCTION', FALSE, 'BOTH', 33, TRUE),
    (50, 'ADJUSTMENT DEDUCTION CAUSE', 'ADJ_DEDUC_CAUSE', 'NO_EFFECT', FALSE, 'REGULAR', NULL, FALSE),
    (51, 'Non-refundable Loan Principle Repayment Installment', 'NREFLOAN_PRIN', 'DEDUCTION', TRUE, 'BOTH', NULL, FALSE),
    (52, 'Term Loan Principle Repayment Installment', 'TERMLOAN_PRIN', 'DEDUCTION', FALSE, 'REGULAR', 45, FALSE),
    (53, 'Term Loan Interest Repayment Installment', 'TERMLOAN_INT', 'DEDUCTION', FALSE, 'REGULAR', 46, FALSE),
    (54, 'Emergency Loan Principle Repayment Installment', 'EMCYLOAN_PRIN', 'DEDUCTION', FALSE, 'REGULAR', 47, FALSE),
    (55, 'Emergency Loan Interest Repayment Installment', 'EMCYLOAN_INT', 'DEDUCTION', FALSE, 'REGULAR', 48, FALSE),
    (56, 'Non-refundable Loan Interest Repayment Installment', 'NREFLOAN_INT', 'DEDUCTION', TRUE, 'BOTH', NULL, FALSE),
    (57, 'SPECIAL DEDUCTION 1', 'SPL_DEDUC_1', 'DEDUCTION', TRUE, 'BOTH', 52, FALSE),
    (58, 'SPECIAL DEDUC CAUSE 1', 'SPL_DEDUC_CAUSE_1', 'NO_EFFECT', FALSE, 'BOTH', NULL, FALSE),
    (59, 'SPECIAL DEDUCTION 2', 'SPL_DEDUC_2', 'DEDUCTION', TRUE, 'BOTH', 54, FALSE),
    (60, 'SPECIAL DEDUC CAUSE 2', 'SPL_DEDUC_CAUSE_2', 'NO_EFFECT', FALSE, 'BOTH', NULL, FALSE),
    (61, 'Arrear HRA', 'ARR_HRA', 'EARNING', FALSE, 'REGULAR', NULL, TRUE),
    (62, 'National Pension Scheme Contribution', 'E_NPS', 'DEDUCTION', FALSE, 'REGULAR', 25, TRUE)
ON CONFLICT (head_count) DO NOTHING;

INSERT INTO payroll_statutory_heads (stat_head_count, stat_head_descr, stat_head_short_name) VALUES
    (1, 'Contributory Provident Fund', 'CPF'),
    (2, 'Voluntary Provident Fund', 'VPF'),
    (3, 'JCI Contributory Provident Fund', 'JCPF'),
    (4, 'Pension Fund', 'PENSION'),
    (5, 'CPF Loan Principle Repayment Installment', 'CPF LOAN PRIN.'),
    (6, 'CPF Loan Interest Repayment Installment', 'CPF LOAN INT.'),
    (7, 'Non-refundable Loan Principle Repayment Installment', 'NONREF. LOAN PRIN.'),
    (8, 'Non-refundable Loan Interest Repayment Installment', 'NONREF. LOAN INT.'),
    (9, 'CPF Loan Sanctioned Amount', 'CPF LOAN SANC.'),
    (10, 'Non-refundable Loan Sanctioned Amount', 'NONREF. LOAN SANC.'),
    (11, 'Arrear Contributory Provident Fund', 'ARR_CPF'),
    (12, 'Arrear JCI Contributory Provident Fund', 'Arrear_JCPF'),
    (13, 'Arrear Pension Fund', 'Arrear_PENSION'),
    (14, 'Employee''s Contribution to National Pention Scheme', 'E_NPS'),
    (15, 'Employer''s Contribution to National Pention Scheme', 'J_NPS')
ON CONFLICT (stat_head_count) DO NOTHING;

-- grade_scale_id values 2-15 are E8-E0/S5-S1 in V48's own insertion order (BIGSERIAL, so id 1 = E9,
-- 2 = E8, ... 15 = S1) - E9 (Board) has no transport-allowance row, matching the live data exactly.
INSERT INTO payroll_transport_allowance_rates (grade_scale_id, city_class, base_rate, effective_from) VALUES
    (2, 'X', 7200.00, '2020-04-01'), (2, 'Y', 3600.00, '2020-04-01'), (2, 'Z', 3600.00, '2020-04-01'),
    (3, 'X', 7200.00, '2020-04-01'), (3, 'Y', 3600.00, '2020-04-01'), (3, 'Z', 3600.00, '2020-04-01'),
    (4, 'X', 7200.00, '2020-04-01'), (4, 'Y', 3600.00, '2020-04-01'), (4, 'Z', 3600.00, '2020-04-01'),
    (5, 'X', 7200.00, '2020-04-01'), (5, 'Y', 3600.00, '2020-04-01'), (5, 'Z', 3600.00, '2020-04-01'),
    (6, 'X', 7200.00, '2020-04-01'), (6, 'Y', 3600.00, '2020-04-01'), (6, 'Z', 3600.00, '2020-04-01'),
    (7, 'X', 7200.00, '2020-04-01'), (7, 'Y', 3600.00, '2020-04-01'), (7, 'Z', 3600.00, '2020-04-01'),
    (8, 'X', 7200.00, '2020-04-01'), (8, 'Y', 3600.00, '2020-04-01'), (8, 'Z', 3600.00, '2020-04-01'),
    (9, 'X', 7200.00, '2020-04-01'), (9, 'Y', 3600.00, '2020-04-01'), (9, 'Z', 3600.00, '2020-04-01'),
    (10, 'X', 3600.00, '2020-04-01'), (10, 'Y', 1800.00, '2020-04-01'), (10, 'Z', 1800.00, '2020-04-01'),
    (11, 'X', 3600.00, '2020-04-01'), (11, 'Y', 1800.00, '2020-04-01'), (11, 'Z', 1800.00, '2020-04-01'),
    (12, 'X', 3600.00, '2020-04-01'), (12, 'Y', 1800.00, '2020-04-01'), (12, 'Z', 1800.00, '2020-04-01'),
    (13, 'X', 3600.00, '2020-04-01'), (13, 'Y', 1800.00, '2020-04-01'), (13, 'Z', 1800.00, '2020-04-01'),
    (14, 'X', 3600.00, '2020-04-01'), (14, 'Y', 1800.00, '2020-04-01'), (14, 'Z', 1800.00, '2020-04-01'),
    (15, 'X', 3600.00, '2020-04-01'), (15, 'Y', 1800.00, '2020-04-01'), (15, 'Z', 1800.00, '2020-04-01')
ON CONFLICT ON CONSTRAINT uq_transport_allowance_rate DO NOTHING;

-- state_master itself has no seed migration anywhere in this file set (V19 only creates the table,
-- per its own comment the row data was loaded by other tooling) - a genuinely fresh environment would
-- have zero rows there, so this seed is guarded by "state_code already exists in state_master" rather
-- than assuming it, to avoid an FK-violation failure on a fresh environment.
INSERT INTO payroll_ptax_slabs (state_code, slab_min, slab_max, tax_amount, special_month, special_month_tax, effective_from)
SELECT v.state_code, v.slab_min, v.slab_max, v.tax_amount, v.special_month, v.special_month_tax, v.effective_from
FROM (VALUES
    ('WB', 0.00, 10000.00, 0.00, NULL, NULL, DATE '2020-04-01'),
    ('WB', 10001.00, 15000.00, 110.00, NULL, NULL, DATE '2020-04-01'),
    ('WB', 15001.00, 20000.00, 130.00, NULL, NULL, DATE '2020-04-01'),
    ('WB', 20001.00, 40000.00, 150.00, NULL, NULL, DATE '2020-04-01'),
    ('WB', 40001.00, NULL, 200.00, 2, 300.00, DATE '2020-04-01'),
    ('AS', 0.00, 10000.00, 0.00, NULL, NULL, DATE '2020-04-01'),
    ('AS', 10001.00, 15000.00, 150.00, NULL, NULL, DATE '2020-04-01'),
    ('AS', 15001.00, 25000.00, 180.00, NULL, NULL, DATE '2020-04-01'),
    ('AS', 25001.00, NULL, 208.00, 3, 212.00, DATE '2020-04-01'),
    ('BH', 0.00, 25000.00, 0.00, NULL, NULL, DATE '2020-04-01'),
    ('BH', 25001.00, 41666.00, 83.33, NULL, NULL, DATE '2020-04-01'),
    ('BH', 41667.00, 83333.00, 166.66, NULL, NULL, DATE '2020-04-01'),
    ('BH', 83334.00, NULL, 200.00, 3, 300.00, DATE '2020-04-01'),
    ('OR', 0.00, 13333.00, 0.00, NULL, NULL, DATE '2020-04-01'),
    ('OR', 13334.00, 25000.00, 125.00, NULL, NULL, DATE '2020-04-01'),
    ('OR', 25001.00, NULL, 200.00, 3, 300.00, DATE '2020-04-01'),
    ('AP', 0.00, 15000.00, 0.00, NULL, NULL, DATE '2020-04-01'),
    ('AP', 15001.00, 20000.00, 150.00, NULL, NULL, DATE '2020-04-01'),
    ('AP', 20001.00, NULL, 200.00, NULL, NULL, DATE '2020-04-01'),
    ('TP', 0.00, 5000.00, 0.00, NULL, NULL, DATE '2020-04-01'),
    ('TP', 5001.00, 7500.00, 100.00, NULL, NULL, DATE '2020-04-01'),
    ('TP', 7501.00, 10000.00, 150.00, NULL, NULL, DATE '2020-04-01'),
    ('TP', 10001.00, NULL, 208.00, 3, 212.00, DATE '2020-04-01')
) AS v(state_code, slab_min, slab_max, tax_amount, special_month, special_month_tax, effective_from)
WHERE EXISTS (SELECT 1 FROM state_master sm WHERE sm.state_code = v.state_code)
ON CONFLICT ON CONSTRAINT uq_ptax_state_slab DO NOTHING;
