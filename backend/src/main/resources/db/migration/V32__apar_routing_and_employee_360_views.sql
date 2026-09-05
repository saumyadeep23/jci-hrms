-- Feature 6 (Dynamic Authority Discovery Engine) + the /employees/:id/360 view.
CREATE OR REPLACE VIEW vw_apar_routing_matrix AS
SELECT sub.id AS appraisee_emp_id,
       sub.employee_code AS appraisee_emp_code,
       sub.full_name AS appraisee_name,
       p_sub.id AS appraisee_post_id,
       p_sub.post_code AS appraisee_post_code,
       p_sub.title AS appraisee_post_title,
       inc_sub.assignment_type AS appraisee_assignment_type,
       inc_sub.start_date AS assignment_start_date,
       inc_sub.end_date AS assignment_end_date,
       rep_emp.id AS reporting_officer_emp_id,
       rep_emp.full_name AS reporting_officer_name,
       rep_emp.official_email AS reporting_officer_email,
       p_rep.id AS reporting_post_id,
       p_rep.title AS reporting_post_title,
       inc_rep.assignment_type AS reporting_assignment_type,
       rev_emp.id AS reviewing_officer_emp_id,
       rev_emp.full_name AS reviewing_officer_name,
       rev_emp.official_email AS reviewing_officer_email,
       p_rev.id AS reviewing_post_id,
       p_rev.title AS reviewing_post_title,
       acc_emp.id AS accepting_officer_emp_id,
       acc_emp.full_name AS accepting_officer_name,
       acc_emp.official_email AS accepting_officer_email,
       p_acc.id AS accepting_post_id,
       p_acc.title AS accepting_post_title
FROM post_incumbency inc_sub
JOIN employees sub ON sub.id = inc_sub.employee_id
JOIN post_master p_sub ON p_sub.id = inc_sub.post_id
LEFT JOIN post_master p_rep ON p_rep.id = p_sub.operational_reporting_post_id
LEFT JOIN post_incumbency inc_rep ON inc_rep.post_id = p_rep.id AND inc_rep.is_active = true AND inc_rep.deleted_at IS NULL
LEFT JOIN employees rep_emp ON rep_emp.id = inc_rep.employee_id
LEFT JOIN post_master p_rev ON p_rev.id = p_sub.administrative_reporting_post_id
LEFT JOIN post_incumbency inc_rev ON inc_rev.post_id = p_rev.id AND inc_rev.is_active = true AND inc_rev.deleted_at IS NULL
LEFT JOIN employees rev_emp ON rev_emp.id = inc_rev.employee_id
LEFT JOIN post_master p_acc ON p_acc.id = p_sub.accepting_authority_post_id
LEFT JOIN post_incumbency inc_acc ON inc_acc.post_id = p_acc.id AND inc_acc.is_active = true AND inc_acc.deleted_at IS NULL
LEFT JOIN employees acc_emp ON acc_emp.id = inc_acc.employee_id
WHERE inc_sub.is_active = true AND inc_sub.deleted_at IS NULL;

-- fn_generate_apar_instances_for_cycle: for a given APAR cycle, resolves
-- every post incumbency overlapping it and flags which ones need their own
-- APAR instance - always for a REGULAR assignment, and for an
-- ADDITIONAL_CHARGE/OFFICIATING one only once the officer has held it >= 90
-- days within the cycle (a short-lived additional charge doesn't earn a
-- separate dual-charge APAR).
CREATE OR REPLACE FUNCTION fn_generate_apar_instances_for_cycle(p_cycle_id BIGINT)
RETURNS TABLE(generated_employee_id BIGINT, generated_post_id BIGINT, assignment_type VARCHAR, service_days_in_cycle INT, is_separate_apar_required BOOLEAN)
LANGUAGE plpgsql
AS $$
DECLARE
    v_cycle_start DATE;
    v_cycle_end DATE;
BEGIN
    SELECT start_date, end_date INTO v_cycle_start, v_cycle_end
    FROM apar_cycles WHERE id = p_cycle_id;

    RETURN QUERY
    SELECT
        inc.employee_id,
        inc.post_id,
        inc.assignment_type,
        (LEAST(COALESCE(inc.end_date, v_cycle_end), v_cycle_end) -
         GREATEST(inc.start_date, v_cycle_start) + 1)::INT AS days_served,
        CASE
            WHEN inc.assignment_type = 'REGULAR' THEN true
            WHEN inc.assignment_type IN ('ADDITIONAL_CHARGE', 'OFFICIATING')
                 AND (LEAST(COALESCE(inc.end_date, v_cycle_end), v_cycle_end) - GREATEST(inc.start_date, v_cycle_start) + 1) >= 90
                 THEN true
            ELSE false
        END AS requires_apar
    FROM post_incumbency inc
    JOIN post_master p ON p.id = inc.post_id
    WHERE inc.deleted_at IS NULL
      AND inc.start_date <= v_cycle_end
      AND (inc.end_date IS NULL OR inc.end_date >= v_cycle_start);
END;
$$;

-- GET /api/v1/employees/:id/360: one unified read model per employee across
-- every satellite table built for this feature.
CREATE OR REPLACE VIEW vw_jci_employee_master_360 AS
SELECT e.id AS employee_id,
       e.personnel_no,
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
           WHEN cat.employment_category = 'REGULAR' THEN 'IDA Scale (' || COALESCE(ps.grade, 'NA') || ')'
           WHEN cat.employment_category = 'CASUAL' THEN 'Daily Wage (INR ' || COALESCE(cat.daily_wage_rate::text, '0') || '/day)'
           WHEN cat.employment_category = 'CONTRACTUAL' THEN 'Fixed Lump-Sum (INR ' || COALESCE(cat.fixed_lump_sum_monthly::text, '0') || '/mo)'
           WHEN cat.employment_category = 'OUTSOURCED' THEN 'Third-Party (' || COALESCE(vm.vendor_name, 'Unknown') || ')'
           ELSE 'Unassigned'
       END AS compensation_tier_summary,
       cat.regular_basic_pay,
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
LEFT JOIN employee_employment_categories cat ON cat.employee_id = e.id AND cat.deleted_at IS NULL
LEFT JOIN vendor_master vm ON vm.id = cat.vendor_id
LEFT JOIN pay_scale_master ps ON ps.id = cat.pay_scale_id
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
