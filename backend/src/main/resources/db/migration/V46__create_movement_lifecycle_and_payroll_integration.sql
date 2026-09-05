-- Transfer/Promotion/Release/Joining-Report lifecycle, PayrollComputationService integration inputs,
-- and Soft-GPS-verified joining. Adapted onto this schema's own conventions rather than the
-- UUID/"offices" shape a feature request described: BIGSERIAL/BIGINT ids throughout (matching
-- every other table here), and "office" is this schema's existing ro_master/RegionalOffice
-- (already carries city_class X/Y/Z for HRA tiering and latitude/longitude/geofence_radius_meters
-- for distance/geofence math - see RegionalOffice.java, GeofenceService.java).
--
-- e-Service Book entries for these lifecycle events are written through the EXISTING
-- employee_service_book table/EmployeeServiceBookService (its event_type column is a free-text
-- VARCHAR(50), constrained only at the application layer by CareerEventType - see that enum for
-- the new TRANSFER_RELEASE/TRANSFER_JOINING/TRANSFER_BENEFIT_EL_CREDIT/PAY_FIXATION values added
-- alongside this migration) - no new service-book table is created here.

CREATE TABLE movement_orders (
    id                    BIGSERIAL PRIMARY KEY,
    order_type            VARCHAR(30)  NOT NULL,
    order_ref_no          VARCHAR(100) NOT NULL,
    order_date            DATE         NOT NULL,
    sanctioned_by_role    VARCHAR(100) NOT NULL DEFAULT 'Competent Authority',
    signed_by_employee_id BIGINT REFERENCES employees (id),
    status                VARCHAR(30)  NOT NULL DEFAULT 'PUBLISHED',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_movement_orders_ref_no UNIQUE (order_ref_no),
    CONSTRAINT ck_movement_orders_type CHECK (order_type IN ('TRANSFER', 'PROMOTION', 'TRANSFER_CUM_PROMOTION')),
    CONSTRAINT ck_movement_orders_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED'))
);

CREATE TABLE employee_movement_records (
    id                            BIGSERIAL PRIMARY KEY,
    order_id                      BIGINT       NOT NULL REFERENCES movement_orders (id),
    employee_id                   BIGINT       NOT NULL REFERENCES employees (id),

    transfer_nature                    VARCHAR(30)  NOT NULL DEFAULT 'ADMINISTRATIVE',
    is_transfer_benefit_admissible     BOOLEAN      NOT NULL DEFAULT TRUE,
    request_application_ref            VARCHAR(100),
    request_reason                     VARCHAR(255),

    from_office_id       BIGINT NOT NULL REFERENCES ro_master (id),
    from_department_id   BIGINT REFERENCES departments (id),
    from_designation_id  BIGINT NOT NULL REFERENCES designations (id),
    from_pay_scale       VARCHAR(50),
    to_office_id         BIGINT NOT NULL REFERENCES ro_master (id),
    to_department_id     BIGINT REFERENCES departments (id),
    to_designation_id    BIGINT NOT NULL REFERENCES designations (id),
    to_pay_scale         VARCHAR(50),
    station_distance_km  INT NOT NULL DEFAULT 0,

    -- Promotional Pay Fixation needs a concrete new basic pay figure to apply, which the
    -- feature request's own column list never supplied (from_pay_scale/to_pay_scale are
    -- descriptive band labels, e.g. "IDA 60,000-1,80,000", not a specific figure) - added so
    -- PayrollMovementIntegrationService/EmployeeServiceBookService's PROMOTION/PAY_FIXATION
    -- entries have something to actually apply to employee_employment_categories.regular_basic_pay.
    promotional_basic_pay NUMERIC(12, 2),

    release_order_ref         VARCHAR(100),
    release_date               DATE,
    release_session             VARCHAR(10),
    released_at_dbtimestamp     TIMESTAMPTZ,

    joining_report_no       VARCHAR(100),
    joining_date              DATE,
    joining_db_timestamp       TIMESTAMPTZ,
    joining_session             VARCHAR(10),
    joining_latitude             NUMERIC(9, 6),
    joining_longitude            NUMERIC(9, 6),
    joining_gps_accuracy          NUMERIC(8, 2),
    joining_distance_meters       DOUBLE PRECISION,
    is_geo_verified                 BOOLEAN NOT NULL DEFAULT FALSE,
    submission_ip                    VARCHAR(45),
    joining_remarks                   TEXT,

    admissible_jt_days          INT NOT NULL DEFAULT 0,
    joining_time_availed_days    INT NOT NULL DEFAULT 0,
    unavailed_jt_days             INT NOT NULL DEFAULT 0,
    el_credited_days               INT NOT NULL DEFAULT 0,
    is_el_credited                   BOOLEAN NOT NULL DEFAULT FALSE,
    leave_ledger_txn_id               BIGINT REFERENCES leave_ledger_entries (id),

    probation_period_months     INT NOT NULL DEFAULT 0,
    probation_end_date           DATE,

    payroll_sync_status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    lpc_number                        VARCHAR(100),
    effective_pay_fixation_date        DATE,
    excess_transit_lwp_days             INT NOT NULL DEFAULT 0,

    movement_status      VARCHAR(30) NOT NULL DEFAULT 'ORDERED',
    joining_status         VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED',
    approved_by_officer_id   BIGINT REFERENCES employees (id),
    approved_at               TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_employee_movement_records_joining_report_no UNIQUE (joining_report_no),
    CONSTRAINT ck_employee_movement_records_transfer_nature CHECK (transfer_nature IN ('ADMINISTRATIVE', 'OWN_REQUEST', 'MUTUAL')),
    CONSTRAINT ck_employee_movement_records_release_session CHECK (release_session IS NULL OR release_session IN ('FORENOON', 'AFTERNOON')),
    CONSTRAINT ck_employee_movement_records_joining_session CHECK (joining_session IS NULL OR joining_session IN ('FORENOON', 'AFTERNOON')),
    CONSTRAINT ck_employee_movement_records_payroll_sync_status CHECK (payroll_sync_status IN ('PENDING', 'PROCESSED', 'LPC_ISSUED', 'LPC_ACCEPTED')),
    CONSTRAINT ck_employee_movement_records_movement_status CHECK (movement_status IN ('ORDERED', 'RELIEVED', 'JOINED')),
    CONSTRAINT ck_employee_movement_records_joining_status CHECK (joining_status IN ('NOT_SUBMITTED', 'PENDING_VERIFICATION', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX ix_employee_movement_records_employee_id ON employee_movement_records (employee_id);
CREATE INDEX ix_employee_movement_records_order_id ON employee_movement_records (order_id);
CREATE INDEX ix_employee_movement_records_movement_status ON employee_movement_records (movement_status);
CREATE INDEX ix_employee_movement_records_joining_status ON employee_movement_records (joining_status);

-- Bridge table PayrollComputationService's monthly run reads to apply a mid-cycle transfer's
-- releasing/receiving office day-split, revised HRA tier, and transit JT/LWP days - see that
-- service's own javadoc for why no effective-dated basic-pay/office history exists yet on
-- Employee itself; this table is the additive carrier for a movement's payroll impact instead
-- of requiring that larger change.
CREATE TABLE payroll_movement_inputs (
    id                     BIGSERIAL PRIMARY KEY,
    movement_id            BIGINT NOT NULL REFERENCES employee_movement_records (id),
    employee_id            BIGINT NOT NULL REFERENCES employees (id),
    pay_month              INT NOT NULL,
    pay_year               INT NOT NULL,
    releasing_office_id    BIGINT REFERENCES ro_master (id),
    releasing_office_days  INT NOT NULL DEFAULT 0,
    receiving_office_id    BIGINT REFERENCES ro_master (id),
    receiving_office_days  INT NOT NULL DEFAULT 0,
    revised_basic_pay      NUMERIC(12, 2),
    revised_hra_tier       VARCHAR(10),
    transit_jt_days        INT NOT NULL DEFAULT 0,
    transit_lwp_days       INT NOT NULL DEFAULT 0,
    is_payroll_applied     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_payroll_movement_inputs_movement_month UNIQUE (movement_id, pay_month, pay_year),
    CONSTRAINT ck_payroll_movement_inputs_month CHECK (pay_month BETWEEN 1 AND 12),
    CONSTRAINT ck_payroll_movement_inputs_hra_tier CHECK (revised_hra_tier IS NULL OR revised_hra_tier IN ('X', 'Y', 'Z'))
);

CREATE INDEX ix_payroll_movement_inputs_month_year ON payroll_movement_inputs (pay_year, pay_month);

-- Widens the leave_ledger_entries.source CHECK constraint (V14) to add TRANSFER_JT_CONVERSION
-- (this feature's "Unavailed Joining Time on transfer" EL credit - see JoiningReportService) and,
-- while touching this exact constraint, to also finally include the LeaveLedgerSource enum values
-- ElAccrualService has been writing since V36 (BASELINE_TAKEON, EL_SEMI_ANNUAL_ACCRUAL,
-- EL_EOL_LAPSE_DEDUCTION, EL_ENCASHMENT_DEBIT, ATTENDANCE_PENALTY_REFUND) that V14's original list
-- never covered - a pre-existing gap, not something introduced here, but the correct full list has
-- to be written out regardless since this ALTER replaces the whole CHECK.
ALTER TABLE leave_ledger_entries DROP CONSTRAINT ck_leave_ledger_entries_source;
ALTER TABLE leave_ledger_entries ADD CONSTRAINT ck_leave_ledger_entries_source CHECK (source IN
    ('AUTO_LATE_DEDUCTION', 'COMMUTED_LEAVE_HPL_DEBIT', 'BASELINE_TAKEON', 'EL_SEMI_ANNUAL_ACCRUAL',
     'EL_EOL_LAPSE_DEDUCTION', 'EL_ENCASHMENT_DEBIT', 'ATTENDANCE_PENALTY_REFUND', 'TRANSFER_JT_CONVERSION'));
