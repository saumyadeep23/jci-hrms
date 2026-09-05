-- Qualification and past-service-record master tables for a fully onboarded
-- employee. Mirrors the target schema already agreed for this feature
-- (verification workflow, document S3 references, Indian government-service
-- categorisation) rather than a minimal first cut.
--
-- IF NOT EXISTS / IF EXISTS guards throughout: some dev/staging databases had
-- these two tables (and their trigger) provisioned ahead of this migration
-- while the schema was being agreed. On a fresh database this migration
-- creates everything from scratch as normal; on one of those pre-provisioned
-- databases it becomes a safe no-op for the parts that already match,
-- without ever dropping or altering existing data.
CREATE TABLE IF NOT EXISTS employee_qualifications (
    id                        BIGSERIAL PRIMARY KEY,
    employee_id               BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    qualification_level       VARCHAR(30)  NOT NULL,
    degree_title               VARCHAR(150) NOT NULL,
    specialization             VARCHAR(150),
    board_university           VARCHAR(200) NOT NULL,
    institution_name           VARCHAR(200),
    passing_year                INTEGER      NOT NULL,
    percentage_cgpa            NUMERIC(5,2),
    division_class              VARCHAR(20),
    course_type                 VARCHAR(20)  NOT NULL DEFAULT 'FULL_TIME',
    is_highest_qualification    BOOLEAN      NOT NULL DEFAULT false,
    certificate_document_s3_key VARCHAR(500),
    is_verified                 BOOLEAN      NOT NULL DEFAULT false,
    verified_by                 VARCHAR(150),
    verified_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at                  TIMESTAMPTZ,

    CONSTRAINT employee_qualifications_qualification_level_check
        CHECK (qualification_level IN ('10TH_SECONDARY', '12TH_HIGHER_SECONDARY', 'DIPLOMA', 'GRADUATION',
                                        'POST_GRADUATION', 'DOCTORATE_PHD', 'PROFESSIONAL_CERTIFICATION', 'OTHER')),
    CONSTRAINT employee_qualifications_division_class_check
        CHECK (division_class IN ('DISTINCTION', 'FIRST_CLASS', 'SECOND_CLASS', 'PASS_CLASS',
                                   'GRADE_A', 'GRADE_B', 'GRADE_C')),
    CONSTRAINT employee_qualifications_course_type_check
        CHECK (course_type IN ('FULL_TIME', 'PART_TIME', 'DISTANCE_CORRESPONDENCE', 'ONLINE')),
    CONSTRAINT employee_qualifications_passing_year_check CHECK (passing_year BETWEEN 1950 AND 2100),
    CONSTRAINT employee_qualifications_percentage_cgpa_check CHECK (percentage_cgpa >= 0 AND percentage_cgpa <= 100)
);

CREATE INDEX IF NOT EXISTS idx_emp_qualifications ON employee_qualifications (employee_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS employee_past_service_records (
    id                             BIGSERIAL PRIMARY KEY,
    employee_id                    BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    organization_name              VARCHAR(200) NOT NULL,
    organization_type              VARCHAR(30)  NOT NULL,
    designation_held                VARCHAR(150) NOT NULL,
    from_date                       DATE         NOT NULL,
    to_date                         DATE         NOT NULL,
    total_service_days              INTEGER GENERATED ALWAYS AS (to_date - from_date + 1) STORED,
    last_pay_scale_pattern          VARCHAR(10),
    last_drawn_basic                 NUMERIC(12,2),
    last_drawn_gross                 NUMERIC(12,2),
    is_qualifying_for_pension_gratuity BOOLEAN    NOT NULL DEFAULT false,
    qualifying_service_order_ref     VARCHAR(100),
    reason_for_leaving                VARCHAR(150),
    experience_certificate_s3_key     VARCHAR(500),
    relieving_noc_document_s3_key     VARCHAR(500),
    is_verified                       BOOLEAN      NOT NULL DEFAULT false,
    verified_by                       VARCHAR(150),
    verified_at                       TIMESTAMPTZ,
    created_at                        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at                        TIMESTAMPTZ,

    CONSTRAINT chk_past_service_dates CHECK (to_date >= from_date),
    CONSTRAINT employee_past_service_records_organization_type_check
        CHECK (organization_type IN ('CENTRAL_GOVT', 'STATE_GOVT', 'CENTRAL_PSU', 'STATE_PSU', 'AUTONOMOUS_BODY',
                                      'DEFENCE_ARMY_NAVY_AIRFORCE', 'PRIVATE_SECTOR', 'OTHER')),
    CONSTRAINT employee_past_service_records_last_pay_scale_pattern_check
        CHECK (last_pay_scale_pattern IN ('IDA', 'CDA', 'CONSOLIDATED', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_emp_past_service ON employee_past_service_records (employee_id) WHERE deleted_at IS NULL;

-- Nudges employees.updated_at whenever a past-service record that counts
-- toward pension/gratuity qualifying service changes, so anything caching
-- off an employee's updated_at (e.g. downstream sync jobs) picks up the
-- change even though the qualifying total itself isn't materialized on
-- employees today.
CREATE OR REPLACE FUNCTION fn_sync_qualifying_past_service()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_total_qualifying_days INT := 0;
BEGIN
    SELECT COALESCE(SUM(total_service_days), 0)
    INTO v_total_qualifying_days
    FROM employee_past_service_records
    WHERE employee_id = COALESCE(NEW.employee_id, OLD.employee_id)
      AND is_qualifying_for_pension_gratuity = true
      AND deleted_at IS NULL;

    UPDATE employees
    SET updated_at = now()
    WHERE id = COALESCE(NEW.employee_id, OLD.employee_id);

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_past_service_qualifying_sync ON employee_past_service_records;
CREATE TRIGGER trg_past_service_qualifying_sync
    AFTER INSERT OR DELETE OR UPDATE ON employee_past_service_records
    FOR EACH ROW EXECUTE FUNCTION fn_sync_qualifying_past_service();
