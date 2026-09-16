-- RBAC foundation (docs/security/RBAC_IMPLEMENTATION.md), phase B/C of the RBAC + Onboarding
-- implementation. Additive only; never touches V1-V93.
--
-- Design: roles/permissions are DB-backed reference data (not Java enums) so the permission
-- catalogue can evolve without a code deploy. user_role_assignments is append-oriented - a
-- revocation sets revoked_by/revoked_at rather than deleting the row, preserving history
-- (RBAC_SECURITY_REQUIREMENTS.md's role/scope audit history requirement) without a separate
-- history table duplicating the same columns.

CREATE TABLE permissions (
    code        VARCHAR(60) PRIMARY KEY,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE roles (
    code        VARCHAR(40) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    -- Purely informational classification (RBAC_PERMISSION_MATRIX.md) - never read by
    -- authorization code, only by the docs/reporting layer, so a role's actual authority is
    -- always whatever is in role_permissions, never this label.
    financial_authority BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE role_permissions (
    role_code       VARCHAR(40) NOT NULL REFERENCES roles(code),
    permission_code VARCHAR(60) NOT NULL REFERENCES permissions(code),
    PRIMARY KEY (role_code, permission_code)
);

-- application_users.employee_id is unique: at most one active HRMS user identity per employee
-- (RBAC_SECURITY_REQUIREMENTS.md / ONBOARDING_SECURITY_REQUIREMENTS.md employee/user uniqueness
-- invariant), enforced at the DB level rather than only in application code.
CREATE TABLE application_users (
    id                BIGSERIAL PRIMARY KEY,
    employee_id       BIGINT NOT NULL UNIQUE REFERENCES employees(id),
    username          VARCHAR(255) NOT NULL UNIQUE,
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING_INVITATION',
    identity_provider VARCHAR(60),
    identity_subject  VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_role_assignments (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES application_users(id),
    role_code       VARCHAR(40) NOT NULL REFERENCES roles(code),
    -- SELF / OFFICE / REGION / HO / ALL_JCI. scope_value is the RegionalOffice.id when
    -- scope_type = OFFICE, and null for every other scope type (SELF/HO/ALL_JCI need no extra
    -- value; REGION is modeled here but not yet resolvable - see RbacSecurity javadoc).
    scope_type      VARCHAR(20) NOT NULL,
    scope_value     BIGINT,
    assigned_by     BIGINT REFERENCES application_users(id),
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_by      BIGINT REFERENCES application_users(id),
    revoked_at      TIMESTAMPTZ,
    reason          VARCHAR(500)
);

CREATE INDEX idx_user_role_assignments_user ON user_role_assignments (user_id);

-- Prevents a duplicate ACTIVE (revoked_at IS NULL) assignment of the same role+scope to the same
-- user - the concurrency-safe backstop RBAC_SECURITY_REQUIREMENTS.md's idempotency requirement
-- calls for, not just an application-level check-then-insert.
CREATE UNIQUE INDEX uq_user_role_assignments_active
    ON user_role_assignments (user_id, role_code, scope_type, COALESCE(scope_value, -1))
    WHERE revoked_at IS NULL;

-- Seed: 18 canonical roles (RBAC_SECURITY_REQUIREMENTS.md target role model).
INSERT INTO roles (code, description, financial_authority) VALUES
    ('SYSTEM_ADMIN',   'Technical/application administration - users, roles, configuration. No implicit financial authority (SEC-010).', FALSE),
    ('USER',           'Baseline role every provisioned employee receives, scoped SELF.', FALSE),
    ('HR_ADMIN',       'General HR administration (legacy-equivalent; onboarding authority).', FALSE),
    ('HR_ADMIN_PERS',  'Personnel administration - checker.', FALSE),
    ('HR_MAKER_PERS',  'Personnel administration - maker.', FALSE),
    ('HR_ADMIN_BILL',  'Payroll/billing administration - checker/finalizer.', FALSE),
    ('HR_MAKER_BILL',  'Payroll/billing administration - maker/preparer.', FALSE),
    ('HR_ADMIN_EST',   'Establishment administration - checker.', FALSE),
    ('HR_MAKER_EST',   'Establishment administration - maker.', FALSE),
    ('FIN_ADMIN',      'General finance administration (legacy-equivalent).', TRUE),
    ('FIN_ADMIN_CPF',  'CPF Trust - checker (sanction/disburse/interest post-reverse/settlement approve).', TRUE),
    ('FIN_MAKER_CPF',  'CPF Trust - maker (apply/prepare).', FALSE),
    ('FIN_ADMIN_DISB', 'Payroll disbursement - checker/authorizer.', TRUE),
    ('FIN_MAKER_DISB', 'Payroll disbursement - maker/preparer.', FALSE),
    ('JCIECCS_ADMIN',  'JCIECCS - checker (approve/reverse/reconcile/settle).', TRUE),
    ('JCIECCS_MAKER',  'JCIECCS - maker (prepare/initiate).', FALSE),
    ('IT_ADMIN_STORE', 'Store/inventory administration - checker.', FALSE),
    ('IT_MAKER_STORE', 'Store/inventory administration - maker.', FALSE);

-- Seed: permission catalogue (RBAC_SECURITY_REQUIREMENTS.md / RBAC_PERMISSION_MATRIX.md).
-- Reconciled to what this implementation phase actually consumes - see
-- docs/security/RBAC_PERMISSION_MATRIX.md for the full role -> permission grant table and which
-- of these are wired into a live @PreAuthorize check today versus reserved for a later phase.
INSERT INTO permissions (code, description) VALUES
    ('USER_VIEW',              'View HRMS user accounts'),
    ('USER_PROVISION',         'Provision a new HRMS user account for an existing employee'),
    ('USER_INVITE',            'Send an onboarding invitation'),
    ('USER_INVITE_RESEND',     'Resend a pending onboarding invitation'),
    ('USER_INVITE_REVOKE',     'Revoke a pending onboarding invitation'),
    ('USER_DISABLE',           'Disable/lock an HRMS user account'),
    ('USER_ROLE_ASSIGN',       'Assign or revoke a role/scope on a user'),
    ('EMPLOYEE_SELF_VIEW',     'View own employee record'),
    ('EMPLOYEE_VIEW',          'View another employee record (within scope)'),
    ('CPF_VIEW',               'View CPF loan/withdrawal applications'),
    ('CPF_APPLY_SELF',         'Apply for own CPF loan/withdrawal'),
    ('CPF_PREPARE',            'Prepare/apply a CPF loan/withdrawal on behalf of a member (maker)'),
    ('CPF_SANCTION',           'Sanction a CPF loan/withdrawal (checker)'),
    ('CPF_DISBURSE',           'Disburse a sanctioned CPF loan/withdrawal (checker)'),
    ('CPF_REJECT',             'Reject a CPF loan/withdrawal application'),
    ('CPF_INTEREST_RUN',       'Run CPF interest calculation'),
    ('CPF_INTEREST_POST',      'Post CPF interest (checker)'),
    ('CPF_INTEREST_REVERSE',   'Reverse posted CPF interest (checker)'),
    ('CPF_SETTLEMENT_PREPARE', 'Prepare a CPF settlement (maker)'),
    ('CPF_SETTLEMENT_APPROVE', 'Approve a CPF settlement (checker)'),
    ('PAYROLL_VIEW',           'View payroll batches/records'),
    ('PAYROLL_PREPARE',        'Create/compute/edit a payroll batch (maker)'),
    ('PAYROLL_FINALIZE',       'Finalize a payroll batch (checker)'),
    ('PAYROLL_REVERSE',        'Reverse a finalized payroll batch (checker)'),
    ('DISBURSEMENT_PREPARE',   'Prepare a disbursement (maker)'),
    ('DISBURSEMENT_AUTHORIZE', 'Authorize a disbursement (checker)'),
    ('DISBURSEMENT_REVERSE',   'Reverse a disbursement (checker)'),
    ('JCIECCS_VIEW',           'View JCIECCS records'),
    ('JCIECCS_PREPARE',        'Prepare/initiate a JCIECCS recovery/transaction (maker)'),
    ('JCIECCS_APPROVE',        'Approve a JCIECCS transaction (checker)'),
    ('JCIECCS_REVERSE',        'Reverse a JCIECCS recovery (checker)'),
    ('JCIECCS_RECONCILE',      'Reconcile JCIECCS records (checker)'),
    ('JCIECCS_SETTLE',         'Settle a JCIECCS no-dues/closure (checker)'),
    ('ATTENDANCE_SELF_PUNCH',  'Punch own attendance'),
    ('ATTENDANCE_VIEW',        'View attendance records (within scope)'),
    ('ATTENDANCE_REGULARIZE',  'Submit own attendance regularization'),
    ('ATTENDANCE_APPROVE',     'Approve attendance regularization (within scope)'),
    ('LEAVE_SELF_APPLY',       'Apply for own leave/encashment'),
    ('LEAVE_APPROVE',          'Approve leave/encashment (within scope)'),
    ('SECURITY_DIAGNOSTICS_VIEW', 'View security/audit diagnostics'),
    ('AUDIT_VIEW',             'View audit trail');

-- SYSTEM_ADMIN: technical administration ONLY - deliberately excludes every CPF_/PAYROLL_/
-- DISBURSEMENT_/JCIECCS_ financial permission (SEC-010's core requirement; see
-- RbacFinancialBoundaryTest).
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('SYSTEM_ADMIN', 'USER_VIEW'),
    ('SYSTEM_ADMIN', 'USER_PROVISION'),
    ('SYSTEM_ADMIN', 'USER_INVITE'),
    ('SYSTEM_ADMIN', 'USER_INVITE_RESEND'),
    ('SYSTEM_ADMIN', 'USER_INVITE_REVOKE'),
    ('SYSTEM_ADMIN', 'USER_DISABLE'),
    ('SYSTEM_ADMIN', 'USER_ROLE_ASSIGN'),
    ('SYSTEM_ADMIN', 'SECURITY_DIAGNOSTICS_VIEW'),
    ('SYSTEM_ADMIN', 'AUDIT_VIEW'),
    ('SYSTEM_ADMIN', 'EMPLOYEE_VIEW');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('USER', 'EMPLOYEE_SELF_VIEW'),
    ('USER', 'CPF_APPLY_SELF'),
    ('USER', 'ATTENDANCE_SELF_PUNCH'),
    ('USER', 'ATTENDANCE_REGULARIZE'),
    ('USER', 'LEAVE_SELF_APPLY');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('HR_ADMIN', 'USER_VIEW'), ('HR_ADMIN', 'USER_PROVISION'), ('HR_ADMIN', 'USER_INVITE'),
    ('HR_ADMIN', 'USER_INVITE_RESEND'), ('HR_ADMIN', 'USER_INVITE_REVOKE'),
    ('HR_ADMIN', 'EMPLOYEE_VIEW'), ('HR_ADMIN', 'ATTENDANCE_VIEW'), ('HR_ADMIN', 'ATTENDANCE_APPROVE'),
    ('HR_ADMIN', 'LEAVE_APPROVE');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('HR_MAKER_BILL', 'PAYROLL_VIEW'), ('HR_MAKER_BILL', 'PAYROLL_PREPARE');
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('HR_ADMIN_BILL', 'PAYROLL_VIEW'), ('HR_ADMIN_BILL', 'PAYROLL_FINALIZE'), ('HR_ADMIN_BILL', 'PAYROLL_REVERSE');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('FIN_MAKER_CPF', 'CPF_VIEW'), ('FIN_MAKER_CPF', 'CPF_PREPARE');
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('FIN_ADMIN_CPF', 'CPF_VIEW'), ('FIN_ADMIN_CPF', 'CPF_SANCTION'), ('FIN_ADMIN_CPF', 'CPF_DISBURSE'),
    ('FIN_ADMIN_CPF', 'CPF_REJECT'), ('FIN_ADMIN_CPF', 'CPF_INTEREST_RUN'), ('FIN_ADMIN_CPF', 'CPF_INTEREST_POST'),
    ('FIN_ADMIN_CPF', 'CPF_INTEREST_REVERSE'), ('FIN_ADMIN_CPF', 'CPF_SETTLEMENT_APPROVE');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('FIN_MAKER_DISB', 'DISBURSEMENT_PREPARE');
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('FIN_ADMIN_DISB', 'DISBURSEMENT_AUTHORIZE'), ('FIN_ADMIN_DISB', 'DISBURSEMENT_REVERSE');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('JCIECCS_MAKER', 'JCIECCS_VIEW'), ('JCIECCS_MAKER', 'JCIECCS_PREPARE');
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('JCIECCS_ADMIN', 'JCIECCS_VIEW'), ('JCIECCS_ADMIN', 'JCIECCS_APPROVE'), ('JCIECCS_ADMIN', 'JCIECCS_REVERSE'),
    ('JCIECCS_ADMIN', 'JCIECCS_RECONCILE'), ('JCIECCS_ADMIN', 'JCIECCS_SETTLE');
