-- vw_jci_employee_master_360 (V32) predates regular_pay_fixations (V50) and
-- grade_scale_master (V48) and was never updated in version control to read
-- from them - the checked-in V32 definition still only surfaces
-- cat.regular_basic_pay (a point-in-time snapshot from initial
-- appointment/onboarding, never updated by increments/promotions) with no
-- current-fixation awareness at all, unlike Manpower4TierReportService/
-- IncrementProcessingService, which already read regular_pay_fixations
-- correctly.
--
-- This migration was written after discovering the *running* local dev DB's
-- view had already been hand-edited (outside Flyway, like V56's
-- personnel_no/cpf_ac_no rename) to exactly this shape - this file captures
-- that already-applied definition into version control rather than
-- inventing a new one, and is a no-op on that dev DB. New columns:
--   - current_basic_pay = COALESCE(rpf.basic_pay, cat.regular_basic_pay) -
--     the actual current monthly basic, replacing the old regular_basic_pay
--     column name so nothing can accidentally keep reading the stale value.
--   - entry_basic_pay = cat.regular_basic_pay, kept verbatim and renamed
--     only for clarity - this is strictly the historical entry/appointment
--     basic pay per PIMS_SPEC.md, never touched by increments.
--   - current_scale_code = rpf.scale_code, increment_cycle = rpf.increment_cycle,
--     current_pay_effective_date = rpf.effective_from - all sourced from the
--     employee's current (is_current = true) fixation row.
-- scale_grade (designation's own default grade) is deliberately left as-is,
-- not coalesced with rpf.scale_code - it's a different concept (the post's
-- sanctioned grade vs. the employee's actual fixated pay grade, which may
-- differ e.g. during a promotion in progress).
--
-- CREATE OR REPLACE VIEW can change a column's defining expression but not
-- its name (an underlying table column rename, like V56's personnel_no ->
-- cpf_ac_no, doesn't rename this view's own output column - a view's output
-- columns are fixed at creation, independent of the source column's current
-- name), so that rename needs an explicit ALTER VIEW first. Idempotent for
-- the same reason V56's DO block is: safe whether this runs against a fresh
-- DB or the already-updated dev DB.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'vw_jci_employee_master_360'
          AND column_name = 'personnel_no'
    ) THEN
        ALTER VIEW public.vw_jci_employee_master_360 RENAME COLUMN personnel_no TO cpf_ac_no;
    END IF;
END $$;

CREATE OR REPLACE VIEW vw_jci_employee_master_360 AS
SELECT e.id AS employee_id,
       e.cpf_ac_no,
       e.employee_code,
       e.hrms_user_id,
       e.full_name,
       e.salutation,
       e.date_of_birth,
       DATE_PART('year', AGE(e.date_of_birth::timestamptz))::INT AS age,
       e.gender,
       e.marital_status,
       e.blood_group,
       e.pan_number,
       e.aadhaar_ref_number,
       e.personal_email,
       e.official_email,
       e.phone AS personal_mobile,
       e.official_mobile,
       e.status AS employment_status,
       e.date_of_joining,
       cat.employment_category,
       CASE
           WHEN cat.employment_category = 'REGULAR' THEN 'IDA Scale (' || COALESCE(rpf.scale_code, gsm.scale_code, cat.scale_code, 'NA') || ')'
           WHEN cat.employment_category = 'CASUAL' THEN 'Daily Wage (INR ' || COALESCE(cat.daily_wage_rate::text, '0') || '/day)'
           WHEN cat.employment_category = 'CONTRACTUAL' THEN 'Fixed Lump-Sum (INR ' || COALESCE(cat.fixed_lump_sum_monthly::text, '0') || '/mo)'
           WHEN cat.employment_category = 'OUTSOURCED' THEN 'Third-Party (' || COALESCE(vm.vendor_name, 'Unknown') || ')'
           ELSE 'Unassigned'
       END AS compensation_tier_summary,
       COALESCE(rpf.basic_pay, cat.regular_basic_pay) AS current_basic_pay,
       cat.regular_basic_pay AS entry_basic_pay,
       rpf.scale_code AS current_scale_code,
       rpf.increment_cycle,
       rpf.effective_from AS current_pay_effective_date,
       cat.daily_wage_rate,
       cat.fixed_lump_sum_monthly,
       cat.monthly_ctc,
       vm.vendor_name AS outsourced_vendor_name,
       p.id AS current_post_id,
       p.post_code AS current_post_code,
       p.title AS current_post_title,
       inc.assignment_type AS current_assignment_type,
       inc.start_date AS post_assignment_start_date,
       dept.code AS department_code,
       dept.name AS department_name,
       des.code AS designation_code,
       des.title AS designation_title,
       des.scale_grade,
       ro.ro_code,
       ro.ro_name,
       dpc.dpc_code,
       dpc.dpc_name,
       ba.bank_name AS active_bank_name,
       ba.bank_branch AS active_bank_branch,
       ba.bank_account_number AS active_bank_account_no,
       ba.bank_ifsc AS active_bank_ifsc,
       ba.status AS bank_verification_status,
       sp.social_category,
       sp.is_pwbd,
       sp.disability_type,
       sup.superannuation_date,
       sup.is_board_director,
       sup.calculation_basis AS superannuation_calculation_basis,
       sup.pension_settlement_status
FROM employees e
LEFT JOIN employee_employment_categories cat ON cat.employee_id = e.id AND cat.deleted_at IS NULL AND cat.is_active = true
LEFT JOIN vendor_master vm ON vm.id = cat.vendor_id
LEFT JOIN grade_scale_master gsm ON gsm.id = cat.pay_scale_id OR gsm.scale_code = cat.scale_code
LEFT JOIN regular_pay_fixations rpf ON rpf.employee_id = e.id AND rpf.is_current = true
LEFT JOIN post_incumbency inc ON inc.employee_id = e.id AND inc.is_active = true AND inc.assignment_type = 'REGULAR' AND inc.deleted_at IS NULL
LEFT JOIN post_master p ON p.id = inc.post_id
LEFT JOIN departments dept ON dept.id = p.department_id
LEFT JOIN designations des ON des.id = p.designation_id
LEFT JOIN ro_master ro ON ro.id = p.ro_id
LEFT JOIN dpc_master dpc ON dpc.id = p.dpc_id
LEFT JOIN employee_bank_accounts ba ON ba.employee_id = e.id AND ba.is_primary_disbursal = true AND ba.status = 'ACTIVE' AND ba.deleted_at IS NULL
LEFT JOIN employee_social_profiles sp ON sp.employee_id = e.id
LEFT JOIN employee_superannuation_details sup ON sup.employee_id = e.id
WHERE e.deleted_at IS NULL;
