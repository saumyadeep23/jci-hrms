CREATE TABLE post_master (
    id                                BIGSERIAL PRIMARY KEY,
    post_code                        VARCHAR(30)  NOT NULL,
    title                             VARCHAR(150) NOT NULL,
    department_id                     BIGINT       NOT NULL REFERENCES departments (id),
    designation_id                    BIGINT       NOT NULL REFERENCES designations (id),
    ro_id                             BIGINT REFERENCES ro_master (id),
    dpc_id                            BIGINT REFERENCES dpc_master (id),
    operational_reporting_post_id     BIGINT REFERENCES post_master (id),
    administrative_reporting_post_id  BIGINT REFERENCES post_master (id),
    is_active                         BOOLEAN      NOT NULL DEFAULT true,
    created_at                        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at                        TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_post_master_post_code_active ON post_master (post_code) WHERE deleted_at IS NULL;
CREATE INDEX ix_post_master_department_id ON post_master (department_id);
CREATE INDEX ix_post_master_designation_id ON post_master (designation_id);
CREATE INDEX ix_post_master_ro_id ON post_master (ro_id);
CREATE INDEX ix_post_master_dpc_id ON post_master (dpc_id);
CREATE INDEX ix_post_master_operational_reporting_post_id ON post_master (operational_reporting_post_id);
CREATE INDEX ix_post_master_administrative_reporting_post_id ON post_master (administrative_reporting_post_id);

CREATE TABLE post_incumbency (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT       NOT NULL REFERENCES post_master (id),
    employee_id     BIGINT       NOT NULL REFERENCES employees (id),
    assignment_type VARCHAR(20)  NOT NULL,
    start_date      DATE         NOT NULL,
    end_date        DATE,
    order_reference VARCHAR(100),
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT ck_post_incumbency_assignment_type
        CHECK (assignment_type IN ('SUBSTANTIVE', 'ADDITIONAL_CHARGE', 'ACTING', 'LOOK_AFTER')),
    CONSTRAINT ck_post_incumbency_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX ix_post_incumbency_post_id ON post_incumbency (post_id);
CREATE INDEX ix_post_incumbency_employee_id ON post_incumbency (employee_id);

-- DB-level backstop for the "auto-close prior Substantive incumbent"
-- invariant, enforced primarily in PostIncumbencyService: at most one
-- active Substantive incumbency per post at a time, regardless of whether
-- a write ever bypasses the service layer.
CREATE UNIQUE INDEX uq_post_incumbency_post_substantive_active
    ON post_incumbency (post_id)
    WHERE assignment_type = 'SUBSTANTIVE' AND is_active = true;

CREATE TABLE statutory_roles (
    id                    BIGSERIAL PRIMARY KEY,
    employee_id           BIGINT       NOT NULL REFERENCES employees (id),
    role_name             VARCHAR(50)  NOT NULL,
    appointment_order_ref VARCHAR(100),
    start_date            DATE         NOT NULL,
    end_date              DATE,
    is_active             BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,

    CONSTRAINT ck_statutory_roles_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX ix_statutory_roles_employee_id ON statutory_roles (employee_id);
