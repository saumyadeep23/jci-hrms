CREATE TABLE holidays (
    id            BIGSERIAL PRIMARY KEY,
    holiday_date  DATE         NOT NULL,
    name          VARCHAR(150) NOT NULL,
    holiday_type  VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,

    CONSTRAINT ck_holidays_holiday_type CHECK (holiday_type IN ('GAZETTED', 'RESTRICTED'))
);

CREATE UNIQUE INDEX uq_holidays_date_active ON holidays (holiday_date) WHERE deleted_at IS NULL;

-- Append-only, like audit_log - a leave-balance-affecting transaction should never
-- be edited/deleted after the fact, only ever superseded by another entry. Not
-- written to until Phase C (AttendanceLeaveDeductionService); schema ships now so
-- Phase C is a logic-only diff on top of an already-reviewed table shape.
CREATE TABLE leave_ledger_entries (
    id                            BIGSERIAL PRIMARY KEY,
    employee_id                   BIGINT       NOT NULL REFERENCES employees (id),
    leave_type_id                 BIGINT       NOT NULL REFERENCES leave_types (id),
    entry_date                    DATE         NOT NULL,
    delta_days                    NUMERIC(4,1) NOT NULL,
    description                   VARCHAR(255) NOT NULL,
    source                        VARCHAR(30)  NOT NULL,
    related_daily_attendance_id   BIGINT REFERENCES daily_attendance (id),
    related_leave_application_id  BIGINT REFERENCES leave_applications (id),
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_leave_ledger_entries_source CHECK (source IN
        ('AUTO_LATE_DEDUCTION', 'COMMUTED_LEAVE_HPL_DEBIT'))
);

CREATE INDEX ix_leave_ledger_entries_employee_id ON leave_ledger_entries (employee_id);

-- Fine-grained pipeline status (Phase B, AttendanceAggregationService) alongside the
-- existing coarse `status` column - PayrollComputationService/PayrollReportingService
-- keep reading `status` unchanged; detail_status is additive only. Nullable because
-- nothing populates it until Phase B ships.
ALTER TABLE daily_attendance ADD COLUMN detail_status VARCHAR(30);
ALTER TABLE daily_attendance ADD COLUMN remarks VARCHAR(255);
ALTER TABLE daily_attendance ADD COLUMN leave_application_id BIGINT REFERENCES leave_applications (id);

ALTER TABLE daily_attendance ADD CONSTRAINT ck_daily_attendance_detail_status CHECK (detail_status IS NULL OR detail_status IN
    ('PRESENT', 'GRACE_APPLIED', 'LATE_SHORT_HOURS', 'REQUIRES_REGULARIZATION', 'UNAUTHORIZED_LATE',
     'HALF_DAY_PRESENT', 'HALF_DAY_SHORT', 'HALF_DAY_ABSENT', 'ON_LEAVE', 'HOLIDAY', 'WEEKOFF', 'ABSENT'));

-- 1st/2nd Half CL (Phase D, LeaveValidationService) - unused until then, but shipped
-- now so leave_applications only needs one migration touch. Defaults FULL_DAY so all
-- existing/new rows stay valid without every caller having to specify it.
ALTER TABLE leave_applications ADD COLUMN leave_session VARCHAR(12) NOT NULL DEFAULT 'FULL_DAY';

ALTER TABLE leave_applications ADD CONSTRAINT ck_leave_applications_leave_session CHECK (leave_session IN
    ('FULL_DAY', 'FIRST_HALF', 'SECOND_HALF'));
