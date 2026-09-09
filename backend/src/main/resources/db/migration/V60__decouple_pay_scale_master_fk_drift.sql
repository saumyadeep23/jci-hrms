-- Phase 1 of the pay_scale_master -> grade_scale_master cutover (see the
-- audit this session ran before this migration). Two independent problems
-- addressed here:
--
--   1) employee_employment_categories.pay_scale_id's FK was manually
--      repointed (outside Flyway) from pay_scale_master(id) to
--      grade_scale_master(id), with all 201 populated rows' values
--      data-migrated to match - but the Java entity still mapped that
--      column to the OLD PayScale/pay_scale_master entity, so any code
--      reading it would have silently resolved the wrong (or a
--      nonexistent) pay_scale_master row. scale_code already carries this
--      same information correctly (verified: every one of the 201 rows'
--      scale_code already agrees with grade_scale_master), so pay_scale_id
--      is fully redundant here - finish what the drift started rather than
--      leave a column whose FK and Java mapping disagree.
--   2) employees.pay_scale_id (0 rows) and employee_service_book.pay_scale_id
--      (1 row) still pointed at pay_scale_master and were never migrated -
--      the latter needs an actual data backfill before its column can be
--      dropped.
--
-- pay_scale_master itself is NOT dropped in this migration - see
-- PayScaleController/PayScaleService, now @Deprecated and read-only, not
-- removed. That's Phase 2, pending confirmation the table's extra
-- per-designation/historical rows (85 rows vs grade_scale_master's 15) are
-- genuinely unused - nothing found in this audit reads them that way, but
-- that wants a human sign-off, not just "no code reads it today".

-- Step 0: vw_jci_employee_master_360 (V57) joins grade_scale_master partly
-- via "gsm.id = cat.pay_scale_id" - redundant with its own
-- "gsm.scale_code = cat.scale_code" clause (confirmed: every populated row
-- already agrees on both), but it's a real dependency blocking the column
-- drop below. Drop that redundant join leg.
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
LEFT JOIN grade_scale_master gsm ON gsm.scale_code = cat.scale_code
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

-- Step 1: employee_employment_categories - drop the redundant, wrongly-mapped FK/column.
ALTER TABLE employee_employment_categories DROP CONSTRAINT IF EXISTS fk_eec_grade_scale_master;
ALTER TABLE employee_employment_categories DROP COLUMN IF EXISTS pay_scale_id;

-- Step 2: employees.pay_scale_id - always empty in this dataset, safe to drop outright.
ALTER TABLE employees DROP COLUMN IF EXISTS pay_scale_id;

-- Step 3: employee_service_book - add grade_scale_id, backfill its one real
-- row (id=1, employee 8) by matching the legacy row's pay_scale_master.grade
-- against grade_scale_master.scale_code (globally unique, so this is an
-- unambiguous translation), then drop pay_scale_id.
ALTER TABLE employee_service_book ADD COLUMN grade_scale_id BIGINT REFERENCES grade_scale_master(id);

UPDATE employee_service_book esb
SET grade_scale_id = gsm.id
FROM pay_scale_master ps
JOIN grade_scale_master gsm ON gsm.scale_code = ps.grade
WHERE esb.pay_scale_id = ps.id;

ALTER TABLE employee_service_book DROP COLUMN IF EXISTS pay_scale_id;
