-- Feature: Step 7 family/dependents data + Step 6 recruitment metadata +
-- Feature 5 (Superannuation Engine) - PIMS_SPEC.md.
CREATE TABLE IF NOT EXISTS employee_family_details (
    id               BIGSERIAL PRIMARY KEY,
    employee_id      BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    father_name      VARCHAR(150) NOT NULL,
    mother_name      VARCHAR(150),
    spouse_name      VARCHAR(150),
    spouse_dob       DATE,
    dependent_count  INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT employee_family_details_employee_id_key UNIQUE (employee_id)
);

CREATE TABLE IF NOT EXISTS employee_recruitment_details (
    id                       BIGSERIAL PRIMARY KEY,
    employee_id              BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    recruitment_id           VARCHAR(50),
    advertisement_no         VARCHAR(100),
    recruitment_year         INTEGER      NOT NULL,
    recruitment_mode         VARCHAR(50)  NOT NULL,
    selection_method         VARCHAR(50)  NOT NULL,
    recruitment_agency       VARCHAR(100),
    appointment_letter_no    VARCHAR(100) NOT NULL,
    appointment_letter_date  DATE         NOT NULL,
    offer_letter_date        DATE,
    joining_letter_date      DATE         NOT NULL,
    date_of_joining_psu      DATE         NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT employee_recruitment_details_employee_id_key UNIQUE (employee_id),
    CONSTRAINT employee_recruitment_details_recruitment_mode_check
        CHECK (recruitment_mode IN ('DIRECT_RECRUITMENT', 'PROMOTION', 'DEPUTATION', 'COMPASSIONATE', 'ABSORPTION')),
    CONSTRAINT employee_recruitment_details_recruitment_year_check CHECK (recruitment_year >= 1950)
);

CREATE TABLE IF NOT EXISTS employee_superannuation_details (
    id                              BIGSERIAL PRIMARY KEY,
    employee_id                     BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    superannuation_date             DATE         NOT NULL,
    retirement_type                 VARCHAR(50)  NOT NULL DEFAULT 'SUPERANNUATION',
    is_board_director               BOOLEAN      NOT NULL DEFAULT false,
    director_appointment_date       DATE,
    initial_term_expiry_date        DATE,
    is_ministry_extended            BOOLEAN      NOT NULL DEFAULT false,
    ministry_extension_order_no     VARCHAR(100),
    ministry_extension_order_date   DATE,
    ministry_extended_upto          DATE,
    calculation_basis               VARCHAR(100),
    retirement_order_no             VARCHAR(100),
    actual_retirement_date          DATE,
    pension_settlement_status       VARCHAR(50)  NOT NULL DEFAULT 'NOT_DUE',
    gratuity_settlement_status      VARCHAR(50)  NOT NULL DEFAULT 'NOT_DUE',
    leave_encashment_days           INTEGER      DEFAULT 0,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT employee_superannuation_details_employee_id_key UNIQUE (employee_id),
    CONSTRAINT employee_superannuation_details_retirement_type_check
        CHECK (retirement_type IN ('SUPERANNUATION', 'VOLUNTARY_VRS', 'COMPULSORY', 'MEDICAL_INVALIDATION', 'RESIGNATION', 'TERMINATION', 'DEATH')),
    CONSTRAINT employee_superannuation_details_pension_settlement_status_check
        CHECK (pension_settlement_status IN ('NOT_DUE', 'INITIATED', 'IN_PROCESS', 'SETTLED', 'WITHHELD')),
    CONSTRAINT employee_superannuation_detail_gratuity_settlement_status_check
        CHECK (gratuity_settlement_status IN ('NOT_DUE', 'INITIATED', 'SETTLED', 'WITHHELD'))
);

-- Referenced (LEFT JOIN) by vw_jci_employee_master_360 - not one of the 8
-- onboarding steps in PIMS_SPEC.md, but part of the target schema it reads.
CREATE TABLE IF NOT EXISTS employee_social_profiles (
    id                      BIGSERIAL PRIMARY KEY,
    employee_id             BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    social_category         VARCHAR(20)  NOT NULL DEFAULT 'GEN',
    sub_caste_community     VARCHAR(100),
    is_pwbd                 BOOLEAN      NOT NULL DEFAULT false,
    disability_type         VARCHAR(100),
    disability_percentage   NUMERIC(5,2),
    is_ex_serviceman        BOOLEAN      NOT NULL DEFAULT false,
    is_sports_quota         BOOLEAN      NOT NULL DEFAULT false,
    reservation_cert_no     VARCHAR(100),
    cert_issuing_authority  VARCHAR(150),
    cert_issue_date         DATE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT employee_social_profiles_employee_id_key UNIQUE (employee_id),
    CONSTRAINT employee_social_profiles_social_category_check
        CHECK (social_category IN ('GEN', 'SC', 'ST', 'OBC_NCL', 'EWS', 'OTHER')),
    CONSTRAINT employee_social_profiles_disability_percentage_check
        CHECK (disability_percentage IS NULL OR (disability_percentage >= 0 AND disability_percentage <= 100))
);

-- Feature 5: Regular staff retire at 58, Board Directors (MD/CMD/DF/DM - the
-- Director category_type on their currently-held post's designation) at 60
-- OR 5 years from appointment, whichever is earlier - both rules push the
-- date to the last day of the birth month (or the preceding month, if born
-- on the 1st). Fires whenever date_of_birth is set/changed; also consults
-- employee_superannuation_details for a manually-recorded director flag/
-- appointment date/ministry extension, since those aren't columns on
-- employees itself.
CREATE OR REPLACE FUNCTION fn_calculate_jci_superannuation_date()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_dob DATE;
    v_target_bday DATE;
    v_age_superannuation_date DATE;
    v_final_retirement_date DATE;
    v_is_director BOOLEAN := false;
    v_category_type VARCHAR(50);
    v_director_doj DATE;
    v_5yr_term_date DATE;
    v_extended BOOLEAN := false;
    v_calc_basis VARCHAR(100);
    v_existing_is_director BOOLEAN;
    v_existing_director_doj DATE;
BEGIN
    v_dob := NEW.date_of_birth;

    SELECT des.category_type, inc.start_date
    INTO v_category_type, v_director_doj
    FROM post_incumbency inc
    JOIN post_master pm ON pm.id = inc.post_id
    JOIN designations des ON des.id = pm.designation_id
    WHERE inc.employee_id = NEW.id
      AND inc.is_active = true
      AND inc.deleted_at IS NULL
    ORDER BY (des.category_type = 'Director') DESC, inc.start_date ASC
    LIMIT 1;

    IF v_category_type = 'Director' THEN
        v_is_director := true;
    END IF;

    -- SELECT ... INTO sets every target to NULL when it matches zero rows
    -- (true here on an employee's very first INSERT, before any
    -- employee_superannuation_details row exists) - COALESCE inside the
    -- select-list can't protect against that since it never gets evaluated
    -- in that case, so the post_incumbency-derived defaults above are only
    -- overridden when a row is actually FOUND.
    SELECT
        is_board_director,
        director_appointment_date,
        is_ministry_extended,
        ministry_extended_upto
    INTO
        v_existing_is_director,
        v_existing_director_doj,
        v_extended,
        v_5yr_term_date
    FROM employee_superannuation_details
    WHERE employee_id = NEW.id;

    IF FOUND THEN
        v_is_director := COALESCE(v_existing_is_director, v_is_director);
        v_director_doj := COALESCE(v_existing_director_doj, v_director_doj);
    END IF;
    v_extended := COALESCE(v_extended, false);

    IF v_is_director THEN
        v_target_bday := v_dob + INTERVAL '60 years';
    ELSE
        v_target_bday := v_dob + INTERVAL '58 years';
    END IF;

    IF EXTRACT(DAY FROM v_dob) = 1 THEN
        v_age_superannuation_date := (DATE_TRUNC('month', v_target_bday) - INTERVAL '1 day')::DATE;
    ELSE
        v_age_superannuation_date := (DATE_TRUNC('month', v_target_bday) + INTERVAL '1 month - 1 day')::DATE;
    END IF;

    IF v_is_director THEN
        IF v_director_doj IS NOT NULL THEN
            v_5yr_term_date := (v_director_doj + INTERVAL '5 years')::DATE;

            IF v_extended = true THEN
                v_final_retirement_date := v_age_superannuation_date;
                v_calc_basis := 'Director 60 Years (Ministry Extension Applied)';
            ELSE
                IF v_5yr_term_date < v_age_superannuation_date THEN
                    v_final_retirement_date := v_5yr_term_date;
                    v_calc_basis := 'Director 5-Year Initial Term Expiry (Earlier than 60 yrs)';
                ELSE
                    v_final_retirement_date := v_age_superannuation_date;
                    v_calc_basis := 'Director 60 Years Superannuation (Earlier than 5-yr tenure)';
                END IF;
            END IF;
        ELSE
            v_final_retirement_date := v_age_superannuation_date;
            v_calc_basis := 'Director 60 Years Superannuation';
        END IF;
    ELSE
        v_final_retirement_date := v_age_superannuation_date;
        v_calc_basis := 'Regular Superannuation (58 Years)';
    END IF;

    INSERT INTO employee_superannuation_details (
        employee_id, superannuation_date, is_board_director, director_appointment_date,
        initial_term_expiry_date, calculation_basis, updated_at
    )
    VALUES (NEW.id, v_final_retirement_date, v_is_director, v_director_doj, v_5yr_term_date, v_calc_basis, now())
    ON CONFLICT (employee_id) DO UPDATE SET
        superannuation_date       = EXCLUDED.superannuation_date,
        is_board_director         = EXCLUDED.is_board_director,
        director_appointment_date = EXCLUDED.director_appointment_date,
        initial_term_expiry_date  = EXCLUDED.initial_term_expiry_date,
        calculation_basis         = EXCLUDED.calculation_basis,
        updated_at                = now();

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_calc_jci_superannuation ON employees;
CREATE TRIGGER trg_calc_jci_superannuation
    AFTER INSERT OR UPDATE OF date_of_birth ON employees
    FOR EACH ROW EXECUTE FUNCTION fn_calculate_jci_superannuation_date();

-- sp_apply_director_ministry_extension: a Ministry order extending a
-- Director's tenure up to (but never past) age 60 - recalculates their
-- age-60 date directly rather than re-deriving it through the trigger,
-- since this is an explicit administrative action, not a DOB change.
CREATE OR REPLACE PROCEDURE sp_apply_director_ministry_extension(
    IN p_employee_id BIGINT, IN p_order_no VARCHAR, IN p_order_date DATE
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_dob DATE;
    v_60th_bday DATE;
    v_age_60_date DATE;
BEGIN
    SELECT date_of_birth INTO v_dob FROM employees WHERE id = p_employee_id;

    IF v_dob IS NULL THEN
        RAISE EXCEPTION 'Employee ID % not found.', p_employee_id;
    END IF;

    v_60th_bday := v_dob + INTERVAL '60 years';
    IF EXTRACT(DAY FROM v_dob) = 1 THEN
        v_age_60_date := (DATE_TRUNC('month', v_60th_bday) - INTERVAL '1 day')::DATE;
    ELSE
        v_age_60_date := (DATE_TRUNC('month', v_60th_bday) + INTERVAL '1 month - 1 day')::DATE;
    END IF;

    UPDATE employee_superannuation_details
    SET is_ministry_extended          = true,
        ministry_extension_order_no   = p_order_no,
        ministry_extension_order_date = p_order_date,
        ministry_extended_upto        = v_age_60_date,
        superannuation_date           = v_age_60_date,
        calculation_basis             = 'Director Extended by Ministry till 60 Years of Age',
        updated_at                    = now()
    WHERE employee_id = p_employee_id;
END;
$$;
