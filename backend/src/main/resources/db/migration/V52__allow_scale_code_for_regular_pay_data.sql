-- The onboarding wizard no longer collects the legacy pay_scale_master dropdown for REGULAR
-- employees (grade_scale_master's scale_code, V48, is now the sole selector) - chk_regular_data
-- (V29) required pay_scale_id NOT NULL for every REGULAR row, which would reject every new
-- onboarding submission now that the wizard never populates it. Loosened to accept either: existing
-- rows (and any caller that still supplies pay_scale_id) keep working, new rows satisfy it via
-- scale_code instead. pay_scale_id itself is untouched - see V48/V49's own header comments for why
-- retiring pay_scale_master/pay_scale_id fully is a separate, later, carefully-planned migration.
ALTER TABLE employee_employment_categories DROP CONSTRAINT IF EXISTS chk_regular_data;
ALTER TABLE employee_employment_categories
    ADD CONSTRAINT chk_regular_data
    CHECK (employment_category <> 'REGULAR' OR (regular_basic_pay IS NOT NULL AND (pay_scale_id IS NOT NULL OR scale_code IS NOT NULL)));
