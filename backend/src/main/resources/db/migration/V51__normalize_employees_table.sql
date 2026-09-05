-- Drops employees' denormalized bank_*/present_*/permanent_* snapshot columns now that
-- employee_bank_accounts (V30) and employee_addresses (V30) are the sole source of truth - see
-- Employee.java's class javadoc, which already documented this as the intended end state.
--
-- pay_scale_id is deliberately NOT touched here, despite being asked for: it is still read
-- directly by PayrollComputationService and LastPayCertificateService (and employees.getPayScale()
-- backs EmployeeResponse), and V48/V49's own header comments already record the standing decision
-- that retiring pay_scale_master/pay_scale_id is a separate, later, carefully-planned migration once
-- every dependent (payroll, LPC, service book, increments, movements, joining reports, onboarding,
-- the promotion PDF generator, and a live V32 view) has been moved onto grade_scale_master. Dropping
-- it here would silently break payroll and LPC generation.

-- STEP 1: defensive backfill - in practice a no-op, since employees.bank_* can only ever be set by
-- the fn_sync_employee_bank_change trigger below (Employee.java maps them insertable=false,
-- updatable=false), which never fires without first inserting the employee_bank_accounts row it
-- copies from. Included anyway per-spec as a safety net against any out-of-band data.
INSERT INTO employee_bank_accounts (employee_id, bank_name, bank_account_number, bank_ifsc, bank_branch,
                                     is_primary_disbursal, status, effective_from)
SELECT e.id, e.bank_name, e.bank_account_number, e.bank_ifsc, e.bank_branch, TRUE, 'ACTIVE', CURRENT_DATE
FROM employees e
WHERE e.bank_account_number IS NOT NULL
  AND e.bank_name IS NOT NULL
  AND e.bank_ifsc IS NOT NULL
  AND e.bank_branch IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM employee_bank_accounts ba WHERE ba.employee_id = e.id);

-- Same defensive spirit for present_*/permanent_* - these columns (V22) were never mapped in
-- Employee.java and no application code has written to them since employee_addresses (V30) became
-- the real store, but a legacy data path could conceivably have left values behind.
INSERT INTO employee_addresses (employee_id, address_type, address_line1, city, district, state, pin_code)
SELECT e.id, 'PRESENT', e.present_address_line, e.present_city, e.present_district, e.present_state, e.present_pin_code
FROM employees e
WHERE e.present_address_line IS NOT NULL AND e.present_city IS NOT NULL AND e.present_district IS NOT NULL
  AND e.present_state IS NOT NULL AND e.present_pin_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM employee_addresses a WHERE a.employee_id = e.id AND a.address_type = 'PRESENT');

INSERT INTO employee_addresses (employee_id, address_type, address_line1, city, district, state, pin_code)
SELECT e.id, 'PERMANENT', e.permanent_address_line, e.permanent_city, e.permanent_district, e.permanent_state, e.permanent_pin_code
FROM employees e
WHERE e.permanent_address_line IS NOT NULL AND e.permanent_city IS NOT NULL AND e.permanent_district IS NOT NULL
  AND e.permanent_state IS NOT NULL AND e.permanent_pin_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM employee_addresses a WHERE a.employee_id = e.id AND a.address_type = 'PERMANENT');

-- STEP 2: the bank-sync trigger/function must go before its target columns do.
DROP TRIGGER IF EXISTS trg_employee_bank_change ON employee_bank_accounts;
DROP FUNCTION IF EXISTS fn_sync_employee_bank_change();

ALTER TABLE employees
    DROP COLUMN IF EXISTS bank_name,
    DROP COLUMN IF EXISTS bank_account_number,
    DROP COLUMN IF EXISTS bank_ifsc,
    DROP COLUMN IF EXISTS bank_branch,
    DROP COLUMN IF EXISTS present_address_line,
    DROP COLUMN IF EXISTS present_city,
    DROP COLUMN IF EXISTS present_district,
    DROP COLUMN IF EXISTS present_state,
    DROP COLUMN IF EXISTS present_pin_code,
    DROP COLUMN IF EXISTS permanent_address_line,
    DROP COLUMN IF EXISTS permanent_city,
    DROP COLUMN IF EXISTS permanent_district,
    DROP COLUMN IF EXISTS permanent_state,
    DROP COLUMN IF EXISTS permanent_pin_code;

-- STEP 3: read-optimized current-state view, joining the real schema (employment tier/scale/pay
-- live on employee_employment_categories + grade_scale_master, not on employees itself - see V29/
-- V48/V49/V50) - not yet consumed by any Java code, but available for future reporting use.
CREATE OR REPLACE VIEW v_active_employee_profiles AS
SELECT
    e.id, e.employee_code, e.first_name, e.middle_name, e.last_name, e.full_name,
    cat.employment_category AS employment_type, e.status, e.date_of_joining, e.date_of_birth, e.gender,
    e.personal_email, e.official_email, e.phone, e.photo_url,
    e.designation_id, des.title AS designation_title,
    e.department_id, dep.name AS department_name,
    e.ro_id, ro.ro_name AS regional_office_name,
    e.dpc_id, dpc.dpc_name AS dpc_unit_name,
    cat.scale_code, gsm.cadre,
    rpf.basic_pay AS regular_basic_pay,
    ce.monthly_lumpsum AS contractual_lumpsum,
    od.monthly_ctc AS outsourced_ctc,
    ba.bank_name, ba.bank_account_number, ba.bank_ifsc, ba.bank_branch,
    p_addr.address_line1 AS present_address, p_addr.city AS present_city, p_addr.state AS present_state, p_addr.pin_code AS present_pin,
    perm_addr.address_line1 AS perm_address, perm_addr.city AS perm_city, perm_addr.state AS perm_state, perm_addr.pin_code AS perm_pin
FROM employees e
LEFT JOIN designations des ON e.designation_id = des.id
LEFT JOIN departments dep ON e.department_id = dep.id
LEFT JOIN ro_master ro ON e.ro_id = ro.id
LEFT JOIN dpc_master dpc ON e.dpc_id = dpc.id
LEFT JOIN employee_employment_categories cat ON e.id = cat.employee_id AND cat.deleted_at IS NULL
LEFT JOIN grade_scale_master gsm ON cat.scale_code = gsm.scale_code
LEFT JOIN regular_pay_fixations rpf ON e.id = rpf.employee_id AND rpf.is_current = TRUE
LEFT JOIN contractual_engagements ce ON e.id = ce.employee_id AND ce.is_current = TRUE
LEFT JOIN outsourced_deployments od ON e.id = od.employee_id AND od.is_current = TRUE
LEFT JOIN employee_bank_accounts ba ON e.id = ba.employee_id AND ba.is_primary_disbursal = TRUE AND ba.status = 'ACTIVE' AND ba.deleted_at IS NULL
LEFT JOIN employee_addresses p_addr ON e.id = p_addr.employee_id AND p_addr.address_type = 'PRESENT' AND p_addr.deleted_at IS NULL
LEFT JOIN employee_addresses perm_addr ON e.id = perm_addr.employee_id AND perm_addr.address_type = 'PERMANENT' AND perm_addr.deleted_at IS NULL
WHERE e.deleted_at IS NULL;
