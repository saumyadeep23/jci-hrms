-- Additive link from employee_employment_categories to the new grade_scale_master (V48) so
-- onboarding can drive REGULAR/CONTRACTUAL/OUTSOURCED remuneration off the Board/Executive/Staff
-- scale (min/max basic, contractual lumpsum, outsourced CTC) instead of pay_scale_id, without
-- touching pay_scale_id itself or any of its existing readers (see V48's header comment for the
-- full dependent list). Nullable - existing rows and any onboarding submission that omits it are
-- unaffected.
ALTER TABLE employee_employment_categories
    ADD COLUMN scale_code VARCHAR(10) REFERENCES grade_scale_master (scale_code);

CREATE INDEX IF NOT EXISTS idx_emp_employment_categories_scale_code ON employee_employment_categories (scale_code);
