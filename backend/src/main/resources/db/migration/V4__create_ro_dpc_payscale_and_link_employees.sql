CREATE TABLE ro_master (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    city_class  VARCHAR(1)   NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT ck_ro_master_city_class CHECK (city_class IN ('X', 'Y', 'Z'))
);

CREATE UNIQUE INDEX uq_ro_master_code_active ON ro_master (code) WHERE deleted_at IS NULL;

CREATE TABLE dpc_master (
    id                     BIGSERIAL PRIMARY KEY,
    ro_id                  BIGINT       NOT NULL REFERENCES ro_master (id),
    code                   VARCHAR(20)  NOT NULL,
    name                   VARCHAR(150) NOT NULL,
    district               VARCHAR(100) NOT NULL,
    state                  VARCHAR(100) NOT NULL,
    latitude               NUMERIC(9, 6),
    longitude              NUMERIC(9, 6),
    geofence_radius_meters INTEGER,
    is_active              BOOLEAN      NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_dpc_master_code_active ON dpc_master (code) WHERE deleted_at IS NULL;
CREATE INDEX ix_dpc_master_ro_id ON dpc_master (ro_id);

CREATE TABLE pay_scale_master (
    id             BIGSERIAL PRIMARY KEY,
    scale_type     VARCHAR(3)    NOT NULL,
    grade          VARCHAR(20)   NOT NULL,
    minimum_basic  NUMERIC(12,2) NOT NULL,
    maximum_basic  NUMERIC(12,2) NOT NULL,
    increment_rate NUMERIC(5,2)  NOT NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,

    CONSTRAINT ck_pay_scale_master_scale_type CHECK (scale_type IN ('IDA', 'CDA')),
    CONSTRAINT ck_pay_scale_master_basic_range CHECK (minimum_basic <= maximum_basic)
);

CREATE UNIQUE INDEX uq_pay_scale_master_type_grade_active ON pay_scale_master (scale_type, grade) WHERE deleted_at IS NULL;

-- Link employees to the new master tables. department_id/designation_id
-- are mandatory, replacing the free-text columns from V1; ro_id/dpc_id/
-- pay_scale_id are optional (e.g. an HO-based employee has no RO/DPC).
--
-- NOTE: department_id/designation_id are added NOT NULL with no default,
-- which requires the employees table to be empty at migration time (true
-- for every environment this has run in so far). If this ever runs against
-- an employees table with existing rows, those rows must be backfilled
-- with a department_id/designation_id before this migration, or it will
-- fail on the ADD COLUMN step below.
ALTER TABLE employees
    ADD COLUMN ro_id          BIGINT REFERENCES ro_master (id),
    ADD COLUMN dpc_id         BIGINT REFERENCES dpc_master (id),
    ADD COLUMN pay_scale_id   BIGINT REFERENCES pay_scale_master (id),
    ADD COLUMN department_id  BIGINT NOT NULL REFERENCES departments (id),
    ADD COLUMN designation_id BIGINT NOT NULL REFERENCES designations (id);

ALTER TABLE employees DROP COLUMN department;
ALTER TABLE employees DROP COLUMN designation;

CREATE INDEX ix_employees_ro_id ON employees (ro_id);
CREATE INDEX ix_employees_dpc_id ON employees (dpc_id);
CREATE INDEX ix_employees_pay_scale_id ON employees (pay_scale_id);
CREATE INDEX ix_employees_department_id ON employees (department_id);
CREATE INDEX ix_employees_designation_id ON employees (designation_id);
