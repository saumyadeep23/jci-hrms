CREATE TABLE apar_cycles (
    id          BIGSERIAL PRIMARY KEY,
    cycle_year  VARCHAR(20) NOT NULL UNIQUE,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_apar_cycles_status
        CHECK (status IN ('INITIATED', 'SELF_APPRAISAL', 'REPORTING', 'REVIEWING', 'ACCEPTING', 'DISCLOSED', 'CLOSED')),
    CONSTRAINT ck_apar_cycles_dates CHECK (end_date >= start_date)
);

CREATE TABLE employee_apars (
    id                       BIGSERIAL PRIMARY KEY,
    apar_cycle_id            BIGINT        NOT NULL REFERENCES apar_cycles (id),
    employee_id              BIGINT        NOT NULL REFERENCES employees (id),
    reporting_officer_id     BIGINT        NOT NULL REFERENCES employees (id),
    reviewing_officer_id     BIGINT        NOT NULL REFERENCES employees (id),
    accepting_authority_id   BIGINT        NOT NULL REFERENCES employees (id),
    status                   VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    self_appraisal_text      TEXT,
    reporting_score          NUMERIC(4,2),
    reporting_remarks        TEXT,
    reviewing_score          NUMERIC(4,2),
    reviewing_remarks        TEXT,
    final_score              NUMERIC(4,2),
    final_grading            VARCHAR(20),
    representation_text      TEXT,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at                TIMESTAMPTZ,

    CONSTRAINT ck_employee_apars_status CHECK (status IN (
        'DRAFT', 'SUBMITTED_BY_EMPLOYEE', 'REPORTED', 'REVIEWED', 'ACCEPTED',
        'DISCLOSED', 'REPRESENTATION_SUBMITTED', 'FINALIZED')),
    CONSTRAINT ck_employee_apars_final_grading
        CHECK (final_grading IS NULL OR final_grading IN ('OUTSTANDING', 'VERY_GOOD', 'GOOD', 'FAIR', 'POOR'))
);

CREATE UNIQUE INDEX uq_employee_apars_cycle_employee_active
    ON employee_apars (apar_cycle_id, employee_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_employee_apars_employee_id ON employee_apars (employee_id);
