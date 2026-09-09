-- State Master: remote-area allowance fields plus updated_at/deleted_at, the HRA Rate Master, and
-- Employee Company Accommodation (quarter allotments) - all three were already created directly
-- against the shared dev database by other tooling before this migration existed (same situation as
-- V19's state_master/district_master and V66's payroll tables - see those migrations' own comments).
-- This migration reproduces that exact live shape (IF NOT EXISTS / ON CONFLICT DO NOTHING) so it is a
-- no-op there and a real create+seed on a fresh environment.

ALTER TABLE state_master ADD COLUMN IF NOT EXISTS is_remote_area BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE state_master ADD COLUMN IF NOT EXISTS remote_allowance_percentage NUMERIC(5,2) NOT NULL DEFAULT 0.00;
ALTER TABLE state_master ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
-- Unused by StateMasterService (which still deactivates via is_active, not this column) - added only
-- because it is already live; not read or written anywhere in the application yet.
ALTER TABLE state_master ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

DO $$ BEGIN
    ALTER TABLE state_master ADD CONSTRAINT chk_state_remote_allowance_rule
        CHECK (is_remote_area = false AND remote_allowance_percentage = 0.00
            OR is_remote_area = true AND remote_allowance_percentage > 0.00 AND remote_allowance_percentage <= 100.00);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS payroll_hra_rates (
    id              BIGSERIAL     PRIMARY KEY,
    city_class      VARCHAR(5)    NOT NULL CHECK (city_class IN ('X', 'Y', 'Z')),
    rate_percentage NUMERIC(5,2)  NOT NULL CHECK (rate_percentage >= 0 AND rate_percentage <= 100),
    min_amount      NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (min_amount >= 0),
    effective_from  DATE          NOT NULL DEFAULT '2020-04-01',
    effective_to    DATE,
    remarks         VARCHAR(255),
    created_at      TIMESTAMPTZ   DEFAULT now(),
    updated_at      TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT uq_hra_rate_city_date UNIQUE (city_class, effective_from)
);
CREATE INDEX IF NOT EXISTS idx_hra_rates_lookup ON payroll_hra_rates (city_class, effective_from);

-- Real historical JCI HRA slabs (7th CPC DA-crossed-50% revision, 2024-01-01) - matches what is
-- already live exactly.
INSERT INTO payroll_hra_rates (city_class, rate_percentage, min_amount, effective_from, effective_to, remarks) VALUES
    ('X', 27.00, 5400.00, '2021-07-01', '2023-12-31', 'Class X (Kolkata/Metros) - DA <= 50% slab'),
    ('Y', 18.00, 3600.00, '2021-07-01', '2023-12-31', 'Class Y (Tier-2 Cities) - DA <= 50% slab'),
    ('Z', 9.00, 1800.00, '2021-07-01', '2023-12-31', 'Class Z (Rural/DPCs) - DA <= 50% slab'),
    ('X', 30.00, 6750.00, '2024-01-01', NULL, 'Class X (Kolkata/Metros) - DA > 50% slab'),
    ('Y', 20.00, 4500.00, '2024-01-01', NULL, 'Class Y (Tier-2 Cities) - DA > 50% slab'),
    ('Z', 10.00, 2250.00, '2024-01-01', NULL, 'Class Z (Rural/DPCs) - DA > 50% slab')
ON CONFLICT ON CONSTRAINT uq_hra_rate_city_date DO NOTHING;

CREATE TABLE IF NOT EXISTS employee_quarter_allotments (
    id                 BIGSERIAL     PRIMARY KEY,
    employee_id        BIGINT        NOT NULL REFERENCES employees (id) ON DELETE RESTRICT,
    allotment_order_no VARCHAR(100),
    quarter_no         VARCHAR(50)   NOT NULL,
    quarter_type       VARCHAR(30),
    estate_location    VARCHAR(150),
    license_fee        NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (license_fee >= 0),
    water_charges      NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (water_charges >= 0),
    electric_charges   NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (electric_charges >= 0),
    allotted_from      DATE          NOT NULL,
    vacated_on         DATE,
    status             VARCHAR(20)   NOT NULL DEFAULT 'OCCUPIED' CHECK (status IN ('OCCUPIED', 'VACATED', 'SURRENDERED', 'CANCELLED')),
    remarks            VARCHAR(255),
    created_at         TIMESTAMPTZ   DEFAULT now(),
    updated_at         TIMESTAMPTZ   DEFAULT now(),

    CONSTRAINT chk_allotment_dates CHECK (vacated_on IS NULL OR vacated_on >= allotted_from)
);
CREATE INDEX IF NOT EXISTS idx_quarter_emp_dates ON employee_quarter_allotments (employee_id, allotted_from, vacated_on);
