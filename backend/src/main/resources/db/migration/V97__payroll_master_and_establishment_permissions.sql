-- Final RBAC business-authority closure (docs/security/RBAC_MIGRATION_REPORT.md): grants the
-- permissions needed to close the three REQUIRES_BUSINESS_CONFIRMATION items left by V94-V96's
-- initial RBAC seed. Additive only; never touches V1-V96. Both roles referenced here (HR_ADMIN_BILL/
-- HR_MAKER_BILL/HR_ADMIN_EST) already exist from V94 with zero or unrelated grants - only new
-- permissions and role_permissions rows are added.

INSERT INTO permissions (code, description) VALUES
    -- Payroll/statutory MASTER DATA (salary heads, statutory heads, statutory parameters) -
    -- deliberately separate from the existing PAYROLL_* permissions, which gate the payroll RUN
    -- lifecycle (create/compute/finalize/reverse a PayrollBatch), a different domain object.
    ('PAYROLL_MASTER_VIEW',    'View payroll/statutory master data (salary heads, statutory heads, statutory parameters)'),
    ('PAYROLL_MASTER_EDIT',    'Prepare a payroll/statutory master-data change (maker) - reserved: no live endpoint currently exercises a prepare-only step, see RBAC_MIGRATION_REPORT.md FOLLOW_UP_REQUIRED - PAYROLL_MASTER_MAKER_CHECKER'),
    ('PAYROLL_MASTER_APPROVE', 'Authoritatively update payroll/statutory master data (checker) - the single live mutation endpoint plays this role since no separate prepare step exists'),
    -- Establishment reference masters (State/District) - distinct from EMPLOYEE_VIEW/other
    -- employee-record permissions; this is master/reference data maintenance.
    ('ESTABLISHMENT_VIEW',     'View establishment reference masters (State/District)'),
    ('ESTABLISHMENT_MAINTAIN', 'Create/update/activate/deactivate establishment reference masters (State/District) - no maker/checker split exists for this master data today');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('HR_MAKER_BILL', 'PAYROLL_MASTER_VIEW'), ('HR_MAKER_BILL', 'PAYROLL_MASTER_EDIT');
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('HR_ADMIN_BILL', 'PAYROLL_MASTER_VIEW'), ('HR_ADMIN_BILL', 'PAYROLL_MASTER_EDIT'), ('HR_ADMIN_BILL', 'PAYROLL_MASTER_APPROVE');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('HR_ADMIN_EST', 'ESTABLISHMENT_VIEW'), ('HR_ADMIN_EST', 'ESTABLISHMENT_MAINTAIN');
