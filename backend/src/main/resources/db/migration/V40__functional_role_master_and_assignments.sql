-- Functional & Statutory Role Management Subsystem (PIMS/ALMS) - decouples
-- non-sanctioned concurrent roles (HoD, CISO, CPIO, FAA, BoT Secretary,
-- Hindi Officer, Vigilance Officer, Zonal Manager) from the cadre headcount.
-- These are appointments layered on top of an employee's substantive post,
-- not seats in post_master/post_incumbency - zero cadre headcount impact.
--
-- Reconciled against this schema's real conventions, same as V19/V20/V36:
--   * role_id/assignment_id are UUID (gen_random_uuid()) as specified - this
--     schema already has UUID-keyed master tables (state_master,
--     district_master, V19) alongside the BIGSERIAL norm everywhere else, so
--     this isn't a new pattern.
--   * employee_id/department_id/office_id stay BIGINT, matching the real
--     PKs of employees(id), departments(id) and ro_master(id) - the spec's
--     "REFERENCES employees(employee_id)" assumed a UUID employees table
--     that doesn't exist here (employees.id is BIGSERIAL, V1).
--   * There is no standalone "offices" table in this schema. ro_master
--     (V4/V20) is this codebase's Regional/Head Office master (office_type
--     IN HEAD_OFFICE/REGIONAL_OFFICE/WAREHOUSE) - office_id below
--     references it directly rather than creating a duplicate table.
--   * This is distinct from the pre-existing statutory_roles table
--     (V13-era, entity StatutoryRole): that table is a flat employee+
--     free-text-role-name+dates record with no jurisdiction (department/
--     office/zone) and no master-role catalogue. This subsystem is richer
--     (a proper role catalogue plus jurisdiction-scoped assignments feeding
--     the ALMS DOA resolver) and additive - statutory_roles is untouched.

CREATE TABLE functional_role_master (
    role_id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code                      VARCHAR(50)  NOT NULL,
    role_name                      VARCHAR(150) NOT NULL,
    role_category                  VARCHAR(50)  NOT NULL,
    has_financial_delegation       BOOLEAN      NOT NULL DEFAULT FALSE,
    has_administrative_delegation  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active                      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_functional_role_master_role_code UNIQUE (role_code),
    CONSTRAINT ck_functional_role_master_category CHECK (role_category IN ('FUNCTIONAL', 'STATUTORY', 'GOVERNANCE', 'TRUST'))
);

INSERT INTO functional_role_master (role_code, role_name, role_category, has_financial_delegation, has_administrative_delegation) VALUES
    ('HOD',               'Head of Department',                       'FUNCTIONAL', true,  true),
    ('CISO',              'Chief Information Security Officer',       'STATUTORY',  false, true),
    ('CPIO',              'Central Public Information Officer',       'STATUTORY',  false, true),
    ('FAA',               'First Appellate Authority',                'STATUTORY',  false, true),
    ('BOT_SEC',           'Board of Trustees Secretary',               'TRUST',      true,  true),
    ('HINDI_OFFICER',     'Hindi Officer (Rajbhasha Adhikari)',       'STATUTORY',  false, false),
    ('VIGILANCE_OFFICER', 'Part-time Vigilance Officer (PTVO)',       'GOVERNANCE', false, true),
    ('ZONAL_MGR',         'Zonal Manager',                            'FUNCTIONAL', true,  true);

CREATE TABLE employee_functional_role_assignment (
    assignment_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id            UUID         NOT NULL REFERENCES functional_role_master (role_id),
    employee_id        BIGINT       NOT NULL REFERENCES employees (id),
    department_id      BIGINT       REFERENCES departments (id),
    office_id          BIGINT       REFERENCES ro_master (id),
    zone_code          VARCHAR(50),
    office_order_ref   VARCHAR(100) NOT NULL,
    order_date         DATE         NOT NULL,
    valid_from         DATE         NOT NULL,
    valid_to           DATE,
    is_primary_role    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_assignment_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_efra_role_active_dept_office ON employee_functional_role_assignment (role_id, is_active, department_id, office_id);
CREATE INDEX ix_efra_employee_active ON employee_functional_role_assignment (employee_id, is_active);
