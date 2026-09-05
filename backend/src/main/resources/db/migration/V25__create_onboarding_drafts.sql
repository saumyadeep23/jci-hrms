-- Employee code sequence backing EmployeeCodeGeneratorService. Never reset
-- per year (a Postgres SEQUENCE can't do that natively without extra
-- bookkeeping) - the generated code embeds the *generation* year for
-- readability only, not a per-year reset counter.
CREATE SEQUENCE employee_code_seq START WITH 1 INCREMENT BY 1;

-- Backs draft_code generation (EmployeeOnboardingService) - a separate
-- sequence from employee_code_seq so draft numbering and employee-code
-- numbering don't consume each other's values.
CREATE SEQUENCE onboarding_draft_seq START WITH 1 INCREMENT BY 1;

-- Backs the 8-step Employee Onboarding & Draft Saving workflow. Steps 1-4
-- (personal/address/bank-statutory/posting) and steps 5-7 (qualifications/
-- past-service/nominee&dependent arrays) are each persisted as one key of
-- step_payloads, keyed by step number ("1".."7"); step 8 (Review & Submit)
-- writes no payload of its own - it validates 1-4 are present and promotes
-- the accumulated payload into real employees/employee_qualifications/
-- employee_past_service_records/employee_nominees/employee_dependents rows.
-- Kept deliberately separate from the `employees` table itself (rather than
-- an early-inserted Employee row with nullable department/designation/etc.)
-- because those columns are NOT NULL and depended on elsewhere as always
-- populated (e.g. EmployeeResponse.from() dereferences department/designation
-- directly) - a half-onboarded draft must never be visible through that path.
CREATE TABLE employee_onboarding_drafts (
    id                     BIGSERIAL PRIMARY KEY,
    draft_code             VARCHAR(30)  NOT NULL,
    employee_code          VARCHAR(50)  NOT NULL,
    current_step           INTEGER      NOT NULL DEFAULT 1,
    max_step_completed     INTEGER      NOT NULL DEFAULT 0,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    step_payloads          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    initiated_by           VARCHAR(150),
    submitted_employee_id  BIGINT REFERENCES employees (id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    submitted_at           TIMESTAMPTZ,

    CONSTRAINT ck_onboarding_draft_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'CANCELLED')),
    CONSTRAINT ck_onboarding_draft_current_step CHECK (current_step BETWEEN 1 AND 8),
    CONSTRAINT ck_onboarding_draft_max_step CHECK (max_step_completed BETWEEN 0 AND 8)
);

CREATE UNIQUE INDEX uq_onboarding_draft_code ON employee_onboarding_drafts (draft_code);
CREATE UNIQUE INDEX uq_onboarding_draft_employee_code ON employee_onboarding_drafts (employee_code);
CREATE INDEX ix_onboarding_draft_status ON employee_onboarding_drafts (status);
