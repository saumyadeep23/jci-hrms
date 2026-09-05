-- Dual pension schemes (NPS / EPS standard / EPS higher pension) - replaces
-- the never-implemented generic "isPensionEligible" concept with three
-- distinct per-employee eligibility flags plus an NPS PRAN reference.
-- Defaults mirror onboarding's expectation that a REGULAR employee
-- participates in NPS by default, with EPS-95 (EPFO) only applying to
-- employees migrated in from EPS-covered past employment.
ALTER TABLE employees ADD COLUMN IF NOT EXISTS is_nps_eligible BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS is_eps_eligible BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS is_eps_higher_pension_eligible BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS pran_number VARCHAR(12);

-- Higher pension (joint option on actual wages) only ever makes sense when
-- EPS itself applies - enforced in EmployeeService.applyOptionalFields too,
-- this is the DB-level backstop.
ALTER TABLE employees DROP CONSTRAINT IF EXISTS ck_employees_eps_higher_pension_requires_eps;
ALTER TABLE employees ADD CONSTRAINT ck_employees_eps_higher_pension_requires_eps
    CHECK (is_eps_higher_pension_eligible = false OR is_eps_eligible = true);
