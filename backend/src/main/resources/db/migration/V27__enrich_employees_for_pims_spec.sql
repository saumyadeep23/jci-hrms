-- Brings `employees` up to the PIMS_SPEC.md / vw_jci_employee_master_360
-- target shape. Written idempotently (IF NOT EXISTS / DROP...IF EXISTS then
-- re-ADD) because some environments already had this shape provisioned
-- ahead of this migration while the schema was being agreed - see V26's
-- header comment for the same situation with the qualifications tables.

ALTER TABLE employees ADD COLUMN IF NOT EXISTS personnel_no VARCHAR(20);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS hrms_user_id VARCHAR(50);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS salutation VARCHAR(10);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS middle_name VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS name_in_regional_lang VARCHAR(255);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS previous_name VARCHAR(255);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS gender VARCHAR(20);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS marital_status VARCHAR(20);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS blood_group VARCHAR(10);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS nationality VARCHAR(50) NOT NULL DEFAULT 'Indian';
ALTER TABLE employees ADD COLUMN IF NOT EXISTS mother_tongue VARCHAR(50);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS pan_number VARCHAR(10);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS aadhaar_ref_number VARCHAR(20);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS official_email VARCHAR(255);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS official_mobile VARCHAR(20);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS bank_branch VARCHAR(150);

-- email -> personal_email rename (only if a fresh DB still has the old name).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'employees' AND column_name = 'email')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'employees' AND column_name = 'personal_email') THEN
        ALTER TABLE employees RENAME COLUMN email TO personal_email;
    END IF;
END $$;

-- Both NOT NULL per spec Step 1 (personal_email always required; phone is
-- "Personal Mobile", also always required). Safe on a fresh DB (0 rows) and
-- a no-op on an already-populated one (already NOT NULL there).
ALTER TABLE employees ALTER COLUMN phone SET NOT NULL;
ALTER TABLE employees ALTER COLUMN date_of_birth SET NOT NULL;
ALTER TABLE employees ALTER COLUMN salutation SET NOT NULL;
ALTER TABLE employees ALTER COLUMN gender SET NOT NULL;
ALTER TABLE employees ALTER COLUMN marital_status SET NOT NULL;
ALTER TABLE employees ALTER COLUMN pan_number SET NOT NULL;

-- full_name is a generated, always-consistent preview of Step 1's
-- Salutation-less "First [Middle] Last" - the frontend no longer needs to
-- compute/store this itself.
ALTER TABLE employees ADD COLUMN IF NOT EXISTS full_name VARCHAR(255)
    GENERATED ALWAYS AS (first_name || COALESCE(' ' || NULLIF(middle_name, ''), '') || ' ' || last_name) STORED;

ALTER TABLE employees DROP CONSTRAINT IF EXISTS ck_employees_status;
ALTER TABLE employees ADD CONSTRAINT ck_employees_status
    CHECK (status IN ('ACTIVE', 'ON_PROBATION', 'ON_LEAVE', 'SUSPENDED', 'RETIRED', 'RESIGNED', 'DECEASED', 'INACTIVE', 'TERMINATED'));

ALTER TABLE employees DROP CONSTRAINT IF EXISTS ck_employees_salutation;
ALTER TABLE employees ADD CONSTRAINT ck_employees_salutation CHECK (salutation IN ('Mr.', 'Ms.', 'Mrs.', 'Dr.'));

ALTER TABLE employees DROP CONSTRAINT IF EXISTS ck_employees_gender;
ALTER TABLE employees ADD CONSTRAINT ck_employees_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'));

ALTER TABLE employees DROP CONSTRAINT IF EXISTS ck_employees_marital_status;
ALTER TABLE employees ADD CONSTRAINT ck_employees_marital_status CHECK (marital_status IN ('SINGLE', 'MARRIED', 'WIDOWED', 'DIVORCED', 'OTHER'));

ALTER TABLE employees DROP CONSTRAINT IF EXISTS ck_employees_blood_group;
ALTER TABLE employees ADD CONSTRAINT ck_employees_blood_group
    CHECK (blood_group IS NULL OR blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'));

ALTER TABLE employees DROP CONSTRAINT IF EXISTS ck_employees_pan_number;
ALTER TABLE employees ADD CONSTRAINT ck_employees_pan_number CHECK (pan_number ~ '^[A-Z]{5}[0-9]{4}[A-Z]$');

ALTER TABLE employees DROP CONSTRAINT IF EXISTS ck_employees_bank_ifsc;
ALTER TABLE employees ADD CONSTRAINT ck_employees_bank_ifsc
    CHECK (bank_ifsc IS NULL OR bank_ifsc ~ '^[A-Z]{4}0[A-Z0-9]{6}$');

DROP INDEX IF EXISTS uq_employees_email_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_personal_email_active ON employees (personal_email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_pan_number_active ON employees (pan_number) WHERE deleted_at IS NULL AND pan_number IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_personnel_no_active ON employees (personnel_no) WHERE deleted_at IS NULL AND personnel_no IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_hrms_user_id_active ON employees (hrms_user_id) WHERE deleted_at IS NULL AND hrms_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_employees_dob ON employees (date_of_birth);
CREATE INDEX IF NOT EXISTS idx_employees_status ON employees (status) WHERE deleted_at IS NULL;

-- Feature 1 (personnel_no half): employee_code itself is allocated by
-- EmployeeCodeGeneratorService (application-side, per PIMS_SPEC.md's exact
-- MAX+1-over-regex algorithm - a DB trigger can't easily express "only
-- numeric-looking codes count"). personnel_no is the DB's own concern -
-- generated here, transparently, on every INSERT that doesn't already
-- supply one.
CREATE SEQUENCE IF NOT EXISTS seq_employee_personnel_no START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE FUNCTION generate_employee_personnel_no()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.personnel_no IS NULL OR NEW.personnel_no = '' THEN
        NEW.personnel_no := 'EMP' || LPAD(nextval('seq_employee_personnel_no')::text, 6, '0');
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_set_employee_personnel_no ON employees;
CREATE TRIGGER trg_set_employee_personnel_no
    BEFORE INSERT ON employees
    FOR EACH ROW EXECUTE FUNCTION generate_employee_personnel_no();

CREATE OR REPLACE FUNCTION check_employee_dob()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.date_of_birth IS NOT NULL AND NEW.date_of_birth >= CURRENT_DATE THEN
        RAISE EXCEPTION 'date_of_birth must be before current date';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_employee_dob ON employees;
CREATE TRIGGER trg_validate_employee_dob
    BEFORE INSERT OR UPDATE OF date_of_birth ON employees
    FOR EACH ROW EXECUTE FUNCTION check_employee_dob();

-- Dedicated, richer audit trail for the employees table (full before/after
-- row images + a computed changed-fields delta) - separate from the
-- generic audit_logs table the rest of the app uses via AuditLogRecorder,
-- because that one only stores whatever narrow snapshot each entity's
-- auditSnapshot() chooses to expose.
CREATE TABLE IF NOT EXISTS employee_audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT       NOT NULL,
    action          VARCHAR(10)  NOT NULL,
    changed_fields  JSONB,
    old_data        JSONB,
    new_data        JSONB,
    changed_by      VARCHAR(100) DEFAULT COALESCE(current_setting('app.current_user_id', true), SESSION_USER),
    client_ip       INET         DEFAULT inet_client_addr(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT employee_audit_logs_action_check CHECK (action IN ('INSERT', 'UPDATE', 'DELETE'))
);

CREATE INDEX IF NOT EXISTS ix_employee_audit_employee_id ON employee_audit_logs (employee_id);
CREATE INDEX IF NOT EXISTS ix_employee_audit_action ON employee_audit_logs (action);
CREATE INDEX IF NOT EXISTS ix_employee_audit_created_at ON employee_audit_logs (created_at DESC);

CREATE OR REPLACE FUNCTION trg_employees_audit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_changed_fields JSONB := '{}'::jsonb;
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO employee_audit_logs (employee_id, action, changed_fields, old_data, new_data)
        VALUES (NEW.id, 'INSERT', NULL, NULL, to_jsonb(NEW));
        RETURN NEW;

    ELSIF TG_OP = 'UPDATE' THEN
        SELECT jsonb_object_agg(n.key, jsonb_build_object('old', o.value, 'new', n.value))
        INTO v_changed_fields
        FROM jsonb_each(to_jsonb(OLD)) o
        JOIN jsonb_each(to_jsonb(NEW)) n ON o.key = n.key
        WHERE o.value IS DISTINCT FROM n.value;

        IF v_changed_fields IS NOT NULL AND v_changed_fields != '{}'::jsonb THEN
            INSERT INTO employee_audit_logs (employee_id, action, changed_fields, old_data, new_data)
            VALUES (NEW.id, 'UPDATE', v_changed_fields, to_jsonb(OLD), to_jsonb(NEW));
        END IF;
        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO employee_audit_logs (employee_id, action, changed_fields, old_data, new_data)
        VALUES (OLD.id, 'DELETE', NULL, to_jsonb(OLD), NULL);
        RETURN OLD;
    END IF;

    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_employees ON employees;
CREATE TRIGGER trg_audit_employees
    AFTER INSERT OR DELETE OR UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION trg_employees_audit();
