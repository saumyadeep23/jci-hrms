-- Attendance and Leave Management Subsystem (ALMS), Phase 1: schema only.
--
-- Several requested table/column names in the ALMS spec don't match what
-- already exists in this schema. Rather than create confusing, overlapping
-- duplicates, this migration reconciles to the real tables:
--   * "leave_entitlement_balance" is a genuinely new table (richer than the
--     existing per-year leave_balances table from V7) - created fresh below,
--     leave_balances is untouched.
--   * "state_holiday_entries" doesn't exist; RH (Restricted Holiday) is
--     already leave_types.code = 'RH', and the holiday calendar an RH
--     application picks a specific date from is the existing `holidays`
--     table (holiday_type = 'RESTRICTED'). rh_entry_id references holidays.
--   * "service_book_entries" doesn't exist; the existing employee_service_book
--     table (V12) is this codebase's general career/service-event ledger and
--     already has a free-text event_type column that can carry an encashment
--     event. service_book_entry_id references employee_service_book.
--   * "attendance_daily_summary" doesn't exist; daily_attendance (V7) already
--     is the one-row-per-employee-per-day attendance summary, and already has
--     a fine-grained detail_status column (V14) doing exactly what the spec's
--     daily_status would - is_regularized/auto_penalty_debited/
--     total_work_minutes are added to daily_attendance instead of a new table.
--   * leave_applications already has a leave_session column ('FULL_DAY' /
--     'FIRST_HALF' / 'SECOND_HALF', V14) covering the same concept as the
--     spec's half_day_session ('NONE'/'FIRST_HALF'/'SECOND_HALF') - reused
--     rather than adding a second, competing half-day indicator column.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- One-time baseline initialization (opening balances as of go-live/joining,
-- verified against the physical service book before the digital ledger takes
-- over). is_locked defaults true - once verified, a baseline is not meant to
-- be casually edited; only a future phase's correction workflow should ever
-- flip it.
-- ---------------------------------------------------------------------------
CREATE TABLE leave_baseline_initialization (
    baseline_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id                  BIGINT       NOT NULL REFERENCES employees (id),
    leave_type_id                BIGINT       NOT NULL REFERENCES leave_types (id),
    as_on_date                   DATE         NOT NULL,
    opening_balance               NUMERIC(5,2) NOT NULL DEFAULT 0,
    opening_encashable_el         NUMERIC(5,2) NOT NULL DEFAULT 0,
    opening_enjoyable_el          NUMERIC(5,2) NOT NULL DEFAULT 0,
    physical_service_book_folio  VARCHAR(50),
    verification_order_ref       VARCHAR(100),
    verified_by                  BIGINT REFERENCES employees (id),
    is_locked                    BOOLEAN      NOT NULL DEFAULT true,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_leave_baseline_non_negative CHECK (
        opening_balance >= 0 AND opening_encashable_el >= 0 AND opening_enjoyable_el >= 0
    ),
    -- The encashable/enjoyable EL split only applies to EL baselines; leave_type_id
    -- can't be checked against leave_types.code in a single-table CHECK, so a
    -- non-EL baseline (which leaves both sub-fields at 0 regardless of its total
    -- opening_balance) is allowed through the second branch of this OR.
    CONSTRAINT ck_leave_baseline_el_split CHECK (
        opening_encashable_el + opening_enjoyable_el = opening_balance
        OR (opening_encashable_el = 0 AND opening_enjoyable_el = 0)
    )
);

CREATE UNIQUE INDEX uq_leave_baseline_employee_type_date
    ON leave_baseline_initialization (employee_id, leave_type_id, as_on_date);

-- ---------------------------------------------------------------------------
-- Entitlement sub-ledgers: richer per-year balance tracking than the existing
-- leave_balances table, including the EL encashable/enjoyable split. New
-- table, not a replacement - leave_balances and LeaveBalanceService/
-- LeaveBalanceController are untouched by this migration.
-- ---------------------------------------------------------------------------
CREATE TABLE leave_entitlement_balance (
    id                    BIGSERIAL PRIMARY KEY,
    employee_id           BIGINT       NOT NULL REFERENCES employees (id),
    leave_type_id         BIGINT       NOT NULL REFERENCES leave_types (id),
    year                  INTEGER      NOT NULL,

    opening_balance       NUMERIC(5,2) NOT NULL DEFAULT 0,
    credited_days         NUMERIC(5,2) NOT NULL DEFAULT 0,
    availed_days          NUMERIC(5,2) NOT NULL DEFAULT 0,
    reserved_days         NUMERIC(5,2) NOT NULL DEFAULT 0,
    encashed_days         NUMERIC(5,2) NOT NULL DEFAULT 0,
    lapsed_days           NUMERIC(5,2) NOT NULL DEFAULT 0,
    current_balance       NUMERIC(5,2) NOT NULL DEFAULT 0,
    available_balance     NUMERIC(5,2) NOT NULL DEFAULT 0,

    encashable_opening    NUMERIC(5,2) NOT NULL DEFAULT 0,
    encashable_credited   NUMERIC(5,2) NOT NULL DEFAULT 0,
    encashable_availed    NUMERIC(5,2) NOT NULL DEFAULT 0,
    encashable_reserved   NUMERIC(5,2) NOT NULL DEFAULT 0,
    encashable_encashed   NUMERIC(5,2) NOT NULL DEFAULT 0,
    encashable_current    NUMERIC(5,2) NOT NULL DEFAULT 0,
    encashable_available  NUMERIC(5,2) NOT NULL DEFAULT 0,

    enjoyable_opening     NUMERIC(5,2) NOT NULL DEFAULT 0,
    enjoyable_credited    NUMERIC(5,2) NOT NULL DEFAULT 0,
    enjoyable_availed     NUMERIC(5,2) NOT NULL DEFAULT 0,
    enjoyable_reserved    NUMERIC(5,2) NOT NULL DEFAULT 0,
    enjoyable_current     NUMERIC(5,2) NOT NULL DEFAULT 0,
    enjoyable_available   NUMERIC(5,2) NOT NULL DEFAULT 0,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_leave_entitlement_balance_employee_type_year UNIQUE (employee_id, leave_type_id, year),
    CONSTRAINT ck_leave_entitlement_balance_non_negative CHECK (
        opening_balance >= 0 AND credited_days >= 0 AND availed_days >= 0 AND reserved_days >= 0 AND
        encashed_days >= 0 AND lapsed_days >= 0 AND current_balance >= 0 AND available_balance >= 0 AND
        encashable_opening >= 0 AND encashable_credited >= 0 AND encashable_availed >= 0 AND
        encashable_reserved >= 0 AND encashable_encashed >= 0 AND encashable_current >= 0 AND
        encashable_available >= 0 AND enjoyable_opening >= 0 AND enjoyable_credited >= 0 AND
        enjoyable_availed >= 0 AND enjoyable_reserved >= 0 AND enjoyable_current >= 0 AND enjoyable_available >= 0
    ),
    CONSTRAINT ck_leave_entitlement_balance_credited_split CHECK (
        credited_days = encashable_credited + enjoyable_credited
    )
);

CREATE INDEX ix_leave_entitlement_balance_employee_id ON leave_entitlement_balance (employee_id);

-- ---------------------------------------------------------------------------
-- Combined CL + RH & temporal exclusivity
-- ---------------------------------------------------------------------------
ALTER TABLE leave_applications ADD COLUMN group_application_id UUID;
ALTER TABLE leave_applications ADD COLUMN rh_entry_id BIGINT REFERENCES holidays (id);
ALTER TABLE leave_applications ADD COLUMN debited_enjoyable_days NUMERIC(5,2);
ALTER TABLE leave_applications ADD COLUMN debited_encashable_days NUMERIC(5,2);

ALTER TABLE leave_applications
    ADD COLUMN period_range daterange GENERATED ALWAYS AS (daterange(start_date, end_date, '[]')) STORED;

-- Two active/pending, non-deleted applications for the same employee can
-- never occupy the same full day. leave_session = 'FULL_DAY' rows only
-- overlap-check against other FULL_DAY rows here.
ALTER TABLE leave_applications ADD CONSTRAINT excl_leave_applications_full_day_overlap
    EXCLUDE USING gist (employee_id WITH =, period_range WITH &&)
    WHERE (deleted_at IS NULL AND status IN ('PENDING_APPROVAL', 'APPROVED') AND leave_session = 'FULL_DAY');

-- Half-day rows only conflict with another half-day row in the *same* session
-- on an overlapping date (e.g. two FIRST_HALF claims), so a complementary
-- pair like First Half CL + Second Half RH on the same date is allowed.
-- Note: this pair of partial exclusion constraints does not by itself stop a
-- half-day row from overlapping a FULL_DAY row on the same date (the two
-- constraints' WHERE clauses partition FULL_DAY vs non-FULL_DAY rows into
-- disjoint groups) - that cross-check is left to the leave-application
-- service layer in a later phase, matching how this schema already defers
-- cross-cutting validation there (see leave_ledger_entries' Phase C/D notes).
ALTER TABLE leave_applications ADD CONSTRAINT excl_leave_applications_half_day_session_overlap
    EXCLUDE USING gist (employee_id WITH =, period_range WITH &&, leave_session WITH =)
    WHERE (deleted_at IS NULL AND status IN ('PENDING_APPROVAL', 'APPROVED') AND leave_session <> 'FULL_DAY');

-- ---------------------------------------------------------------------------
-- Encashment
-- ---------------------------------------------------------------------------
CREATE TABLE leave_encashment_application (
    id                        BIGSERIAL PRIMARY KEY,
    employee_id               BIGINT       NOT NULL REFERENCES employees (id),
    encashment_type           VARCHAR(20)  NOT NULL,
    el_days_claimed           NUMERIC(5,2) NOT NULL DEFAULT 0,
    hpl_days_claimed          NUMERIC(5,2) NOT NULL DEFAULT 0,

    hr_approval_status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    hr_approved_by            BIGINT REFERENCES employees (id),
    hr_approved_at            TIMESTAMPTZ,
    hr_remarks                TEXT,

    finance_approval_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    finance_approved_by       BIGINT REFERENCES employees (id),
    finance_approved_at       TIMESTAMPTZ,
    finance_remarks           TEXT,

    is_payroll_eligible       BOOLEAN      NOT NULL DEFAULT false,
    service_book_entry_id     BIGINT REFERENCES employee_service_book (id),

    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_leave_encashment_type CHECK (encashment_type IN ('IN_SERVICE_EL', 'SUPERANNUATION', 'SEPARATION')),
    CONSTRAINT ck_leave_encashment_hr_status CHECK (hr_approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_leave_encashment_finance_status CHECK (finance_approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_leave_encashment_non_negative CHECK (el_days_claimed >= 0 AND hpl_days_claimed >= 0),
    CONSTRAINT ck_leave_encashment_in_service_el_rule CHECK (
        encashment_type <> 'IN_SERVICE_EL' OR (el_days_claimed >= 15.00 AND hpl_days_claimed = 0)
    ),
    -- Dual approval integrity: payroll can only be told this is eligible once both HR and Finance have signed off.
    CONSTRAINT ck_leave_encashment_dual_approval CHECK (
        is_payroll_eligible = false OR (hr_approval_status = 'APPROVED' AND finance_approval_status = 'APPROVED')
    )
);

CREATE INDEX ix_leave_encashment_application_employee_id ON leave_encashment_application (employee_id);

-- ---------------------------------------------------------------------------
-- Office timing, anomaly regularization & payroll cutoff
-- ---------------------------------------------------------------------------
CREATE TABLE office_timing_config (
    id                       BIGSERIAL PRIMARY KEY,
    effective_from           DATE        NOT NULL,
    start_time               TIME        NOT NULL DEFAULT '09:45',
    flex_grace_time          TIME        NOT NULL DEFAULT '10:15',
    late_concession_time     TIME        NOT NULL DEFAULT '10:45',
    early_concession_time    TIME        NOT NULL DEFAULT '17:15',
    end_time                 TIME        NOT NULL DEFAULT '18:15',
    full_day_minutes         INTEGER     NOT NULL DEFAULT 510,
    half_day_minutes         INTEGER     NOT NULL DEFAULT 255,
    monthly_concession_cap   INTEGER     NOT NULL DEFAULT 2,
    is_active                BOOLEAN     NOT NULL DEFAULT true,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Circular 53 defaults (see column defaults above); effective_from is a
-- placeholder date, not a confirmed circular-issue date - HR should correct
-- it via a future office_timing_config admin endpoint before this is relied
-- on for real anomaly detection, same caveat as V16's leave-type seed data.
INSERT INTO office_timing_config (effective_from) VALUES ('2020-01-01');

CREATE TABLE attendance_regularization_applications (
    id                        BIGSERIAL PRIMARY KEY,
    employee_id                BIGINT       NOT NULL REFERENCES employees (id),
    attendance_date             DATE         NOT NULL,
    daily_attendance_id         BIGINT REFERENCES daily_attendance (id),
    reason_code                 VARCHAR(30)  NOT NULL,
    remarks                     TEXT,
    corrected_in_time           TIMESTAMPTZ,
    corrected_out_time          TIMESTAMPTZ,
    approval_status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    designated_approver_id      BIGINT REFERENCES employees (id),
    approved_at                 TIMESTAMPTZ,
    approver_remarks            TEXT,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_attendance_regularization_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_attendance_regularization_reason_code CHECK (reason_code IN
        ('FORGOT_PUNCH', 'DEVICE_FAILURE', 'FIELD_DUTY', 'GEOFENCE_ISSUE', 'SYSTEM_ERROR', 'OTHER'))
);

CREATE INDEX ix_attendance_regularization_employee_id ON attendance_regularization_applications (employee_id);

-- daily_attendance is this schema's existing daily attendance summary table
-- (see reconciliation note at the top of this file) - detail_status (V14)
-- already covers the "daily_status" concept, so only the three genuinely new
-- columns are added here.
ALTER TABLE daily_attendance ADD COLUMN is_regularized BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE daily_attendance ADD COLUMN auto_penalty_debited BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE daily_attendance ADD COLUMN total_work_minutes INTEGER;

-- Monthly payroll attendance freeze window (the 26th of the prior month
-- through the 25th of cutoff_month/cutoff_year, per the ALMS spec) - the
-- exact day-of-month boundary is a business rule for whichever service
-- creates these rows, not something this table's constraints hardcode
-- (calendar edge cases like short months make a rigid CHECK brittle).
CREATE TABLE attendance_payroll_cutoff (
    id              BIGSERIAL PRIMARY KEY,
    cutoff_month    INTEGER      NOT NULL,
    cutoff_year     INTEGER      NOT NULL,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    is_frozen       BOOLEAN      NOT NULL DEFAULT false,
    frozen_at       TIMESTAMPTZ,
    frozen_by       BIGINT REFERENCES employees (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_attendance_payroll_cutoff_month_year UNIQUE (cutoff_month, cutoff_year),
    CONSTRAINT ck_attendance_payroll_cutoff_month CHECK (cutoff_month BETWEEN 1 AND 12),
    CONSTRAINT ck_attendance_payroll_cutoff_period CHECK (period_end > period_start)
);
