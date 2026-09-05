CREATE TABLE disciplinary_cases (
    id                       BIGSERIAL PRIMARY KEY,
    case_number              VARCHAR(100)  NOT NULL UNIQUE,
    employee_id              BIGINT        NOT NULL REFERENCES employees (id),
    case_type                VARCHAR(50)   NOT NULL,
    status                   VARCHAR(50)   NOT NULL DEFAULT 'INITIATED',
    charge_sheet_date        DATE,
    inquiry_officer_id       BIGINT REFERENCES employees (id),
    penalty_type             VARCHAR(50),
    penalty_effective_from   DATE,
    penalty_effective_to     DATE,
    remarks                  TEXT,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    is_deleted               BOOLEAN       NOT NULL DEFAULT false,
    deleted_at               TIMESTAMPTZ,

    CONSTRAINT ck_disciplinary_cases_case_type CHECK (case_type IN
        ('CONDUCT_RULES', 'FINANCIAL_IRREGULARITY', 'VIGILANCE', 'ABSENTEEISM')),
    CONSTRAINT ck_disciplinary_cases_status CHECK (status IN
        ('INITIATED', 'CHARGE_SHEET_ISSUED', 'INQUIRY_IN_PROGRESS', 'REPORT_SUBMITTED',
         'PENALTY_IMPOSED', 'EXONERATED', 'CLOSED')),
    CONSTRAINT ck_disciplinary_cases_penalty_type CHECK (penalty_type IS NULL OR penalty_type IN
        ('CENSURE', 'WITHHOLDING_INCREMENT', 'REDUCTION_IN_PAY_SCALE', 'RECOVERY_OF_LOSS',
         'SUSPENSION', 'COMPULSORY_RETIREMENT', 'DISMISSAL', 'NONE'))
);

CREATE INDEX ix_disciplinary_cases_employee_id ON disciplinary_cases (employee_id);

-- generateBankDisbursementFile (Phase 11, PayrollReportingService) needs somewhere
-- to read an employee's bank details from - nothing in the schema captures these
-- anywhere yet. Nullable: existing employees have no bank data on file until an
-- HR/onboarding flow is built to collect it (out of scope here - this phase only
-- consumes the fields, it doesn't add a way to manage them).
ALTER TABLE employees ADD COLUMN bank_name VARCHAR(150);
ALTER TABLE employees ADD COLUMN bank_account_number VARCHAR(30);
ALTER TABLE employees ADD COLUMN bank_ifsc VARCHAR(11);
