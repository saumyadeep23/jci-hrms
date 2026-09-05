CREATE TABLE mobile_punches (
    id                 BIGSERIAL PRIMARY KEY,
    employee_id        BIGINT       NOT NULL REFERENCES employees (id),
    punch_time         TIMESTAMPTZ  NOT NULL,
    punch_type         VARCHAR(3)   NOT NULL,
    latitude           NUMERIC(9,6) NOT NULL,
    longitude          NUMERIC(9,6) NOT NULL,
    accuracy_meters    NUMERIC(6,2),
    is_within_geofence BOOLEAN      NOT NULL,
    review_status      VARCHAR(20)  NOT NULL,
    device_id          VARCHAR(100),
    photo_s3_key       VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_mobile_punches_punch_type CHECK (punch_type IN ('IN', 'OUT')),
    CONSTRAINT ck_mobile_punches_review_status CHECK (review_status IN ('VALID', 'FLAGGED_FOR_REVIEW'))
);

CREATE INDEX ix_mobile_punches_employee_id ON mobile_punches (employee_id);
CREATE INDEX ix_mobile_punches_punch_time ON mobile_punches (punch_time);

CREATE TABLE daily_attendance (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT       NOT NULL REFERENCES employees (id),
    attendance_date     DATE         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    in_time             TIMESTAMPTZ,
    out_time            TIMESTAMPTZ,
    total_working_hours NUMERIC(4,2),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_daily_attendance_status
        CHECK (status IN ('PRESENT', 'ABSENT', 'HALF_DAY', 'ON_LEAVE', 'HOLIDAY', 'WEEKLY_OFF')),
    CONSTRAINT uq_daily_attendance_employee_date UNIQUE (employee_id, attendance_date)
);

CREATE INDEX ix_daily_attendance_employee_id ON daily_attendance (employee_id);

CREATE TABLE leave_types (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(20)  NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    annual_quota          NUMERIC(4,1) NOT NULL,
    max_accumulation_days INTEGER,
    is_encashable         BOOLEAN      NOT NULL DEFAULT false,
    career_limit_days     INTEGER,
    is_active             BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_leave_types_code_active ON leave_types (code) WHERE deleted_at IS NULL;

CREATE TABLE leave_balances (
    id            BIGSERIAL PRIMARY KEY,
    employee_id   BIGINT       NOT NULL REFERENCES employees (id),
    leave_type_id BIGINT       NOT NULL REFERENCES leave_types (id),
    year          INTEGER      NOT NULL,
    credited_days NUMERIC(4,1) NOT NULL DEFAULT 0,
    used_days     NUMERIC(4,1) NOT NULL DEFAULT 0,
    reserved_days NUMERIC(4,1) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_leave_balances_employee_type_year UNIQUE (employee_id, leave_type_id, year),
    CONSTRAINT ck_leave_balances_non_negative
        CHECK (credited_days >= 0 AND used_days >= 0 AND reserved_days >= 0)
);

CREATE INDEX ix_leave_balances_employee_id ON leave_balances (employee_id);

CREATE TABLE leave_applications (
    id                   BIGSERIAL PRIMARY KEY,
    employee_id          BIGINT       NOT NULL REFERENCES employees (id),
    leave_type_id        BIGINT       NOT NULL REFERENCES leave_types (id),
    start_date           DATE         NOT NULL,
    end_date             DATE         NOT NULL,
    total_days           NUMERIC(4,1) NOT NULL,
    reason               TEXT         NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    approver_post_id     BIGINT REFERENCES post_master (id),
    approver_employee_id BIGINT REFERENCES employees (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,

    CONSTRAINT ck_leave_applications_status
        CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_leave_applications_dates CHECK (end_date >= start_date)
);

CREATE INDEX ix_leave_applications_employee_id ON leave_applications (employee_id);
CREATE INDEX ix_leave_applications_status ON leave_applications (status);
