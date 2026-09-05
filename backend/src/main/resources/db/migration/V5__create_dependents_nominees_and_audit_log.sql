CREATE TABLE employee_dependents (
    id                 BIGSERIAL PRIMARY KEY,
    employee_id        BIGINT       NOT NULL REFERENCES employees (id),
    name               VARCHAR(150) NOT NULL,
    relationship       VARCHAR(50)  NOT NULL,
    date_of_birth      DATE,
    is_dependent       BOOLEAN      NOT NULL DEFAULT true,
    is_covered_medical BOOLEAN      NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);

CREATE INDEX ix_employee_dependents_employee_id ON employee_dependents (employee_id);

CREATE TABLE employee_nominees (
    id               BIGSERIAL PRIMARY KEY,
    employee_id      BIGINT       NOT NULL REFERENCES employees (id),
    name             VARCHAR(150) NOT NULL,
    relationship     VARCHAR(50)  NOT NULL,
    share_percentage NUMERIC(5,2) NOT NULL,
    nominee_for      VARCHAR(50)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,

    CONSTRAINT ck_employee_nominees_share_percentage CHECK (share_percentage >= 0 AND share_percentage <= 100)
);

CREATE INDEX ix_employee_nominees_employee_id ON employee_nominees (employee_id);

-- Generic, append-only audit trail. Not soft-deletable (no deleted_at) -
-- audit rows are the record of what happened and shouldn't be hideable
-- the same way business data is.
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    entity_name     VARCHAR(100) NOT NULL,
    entity_id       BIGINT       NOT NULL,
    action          VARCHAR(10)  NOT NULL,
    acting_username VARCHAR(150),
    client_ip       VARCHAR(45),
    before_state    JSONB,
    after_state     JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_audit_logs_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'))
);

CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_name, entity_id);
