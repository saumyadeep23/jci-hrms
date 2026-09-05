-- fn_calculate_jci_superannuation_date (V31) only ever looked at the Director category via the
-- employee's active post_incumbency row - correct for a REGULAR employee whose post assignment goes
-- through the Movement Order flow, but it means directly editing employees.designation_id (the new
-- EditEmployeeModal's Posting & Cadre tab, or any CASUAL/CONTRACTUAL/OUTSOURCED employee who has no
-- post_incumbency row at all) never changed the Director determination, even when the newly-picked
-- designation is itself category_type = 'Director'. Adds that as a second, OR'd signal.
--
-- No trigger-definition change needed: Hibernate's default (non-@DynamicUpdate) UPDATE always
-- includes date_of_birth in its SET list regardless of which fields actually changed, so
-- "AFTER UPDATE OF date_of_birth" already re-fires this function on every EmployeeService.update()
-- call - including ones that only touch designationId or dateOfJoining.
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
    v_own_designation_category VARCHAR(50);
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

    -- Second, OR'd signal: the employee's own designations.category_type (employees.designation_id),
    -- for whoever has no active post_incumbency row to derive it from above.
    IF NOT v_is_director THEN
        SELECT des.category_type INTO v_own_designation_category
        FROM designations des
        WHERE des.id = NEW.designation_id;

        IF v_own_designation_category = 'Director' THEN
            v_is_director := true;
            IF v_director_doj IS NULL THEN
                v_director_doj := NEW.date_of_joining;
            END IF;
        END IF;
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
