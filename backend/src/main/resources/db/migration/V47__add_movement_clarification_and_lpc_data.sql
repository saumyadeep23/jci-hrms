-- Joining-report clarification/re-submission lifecycle, and the detailed line-item data behind
-- a generated Last Pay Certificate (LPC). Adapted onto V46's own BIGINT/BIGSERIAL conventions -
-- "employees(employee_id)" in the originating feature request is this schema's employees(id).

ALTER TABLE employee_movement_records DROP CONSTRAINT ck_employee_movement_records_joining_status;
ALTER TABLE employee_movement_records ADD CONSTRAINT ck_employee_movement_records_joining_status
    CHECK (joining_status IN ('NOT_SUBMITTED', 'PENDING_VERIFICATION', 'CLARIFICATION_REQUESTED', 'ACCEPTED', 'REJECTED'));

ALTER TABLE employee_movement_records ADD COLUMN clarification_remarks TEXT;
ALTER TABLE employee_movement_records ADD COLUMN clarification_requested_by BIGINT REFERENCES employees (id);
ALTER TABLE employee_movement_records ADD COLUMN clarification_requested_at TIMESTAMPTZ;
ALTER TABLE employee_movement_records ADD COLUMN resubmission_count INT NOT NULL DEFAULT 0;
ALTER TABLE employee_movement_records ADD COLUMN resubmitted_at TIMESTAMPTZ;
ALTER TABLE employee_movement_records ADD COLUMN lpc_issue_date DATE;
ALTER TABLE employee_movement_records ADD COLUMN lpc_signatory_name VARCHAR(100);
ALTER TABLE employee_movement_records ADD COLUMN lpc_signatory_designation VARCHAR(100);

-- The detailed, immutable line-item breakdown behind a generated LPC PDF - re-printable exactly
-- as originally certified, separate from employee_movement_records' own lpc_issue_date/signatory_*
-- summary columns (LastPayCertificateService.getOrCreate() writes both together, once).
CREATE TABLE movement_lpc_records (
    id                           BIGSERIAL PRIMARY KEY,
    movement_id                  BIGINT NOT NULL REFERENCES employee_movement_records (id),
    employee_id                  BIGINT NOT NULL REFERENCES employees (id),
    lpc_number                   VARCHAR(100) NOT NULL,
    rate_of_pay_basic            NUMERIC(12, 2) NOT NULL,
    rate_of_da                   NUMERIC(12, 2) NOT NULL,
    rate_of_hra                  NUMERIC(12, 2) NOT NULL,
    rate_of_special_allowance    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    cpf_subscription             NUMERIC(12, 2) NOT NULL,
    cpf_advance_balance          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    festival_advance_balance     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    el_balance_days              INT NOT NULL,
    hpl_balance_days             INT NOT NULL,
    pay_drawn_upto_date          DATE NOT NULL,
    pay_drawn_session            VARCHAR(10) NOT NULL,
    generated_by                 BIGINT REFERENCES employees (id),
    generated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_movement_lpc_records_lpc_number UNIQUE (lpc_number),
    CONSTRAINT uq_movement_lpc_records_movement_id UNIQUE (movement_id),
    CONSTRAINT ck_movement_lpc_records_session CHECK (pay_drawn_session IN ('FORENOON', 'AFTERNOON'))
);
