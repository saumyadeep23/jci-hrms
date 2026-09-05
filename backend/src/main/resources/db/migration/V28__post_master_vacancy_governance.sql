-- Feature 2 (Post Master vacancy governance) - see PIMS_SPEC.md Feature 2.
ALTER TABLE post_master ADD COLUMN IF NOT EXISTS accepting_authority_post_id BIGINT REFERENCES post_master (id) ON DELETE SET NULL;
ALTER TABLE post_master ADD COLUMN IF NOT EXISTS vacancy_status VARCHAR(20) NOT NULL DEFAULT 'VACANT';
ALTER TABLE post_master ADD COLUMN IF NOT EXISTS is_budgeted BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE post_master DROP CONSTRAINT IF EXISTS chk_post_master_vacancy_status;
ALTER TABLE post_master ADD CONSTRAINT chk_post_master_vacancy_status
    CHECK (vacancy_status IN ('VACANT', 'OCCUPIED', 'FROZEN', 'ABOLISHED'));

CREATE INDEX IF NOT EXISTS idx_post_master_vacancy ON post_master (vacancy_status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_post_master_accepting_authority_post_id ON post_master (accepting_authority_post_id);

-- Backfill: any post already OCCUPIED by an existing active REGULAR
-- incumbency should read as OCCUPIED even before the trigger below exists
-- to keep it in sync going forward.
UPDATE post_master pm
SET vacancy_status = 'OCCUPIED'
WHERE vacancy_status = 'VACANT'
  AND EXISTS (
      SELECT 1 FROM post_incumbency pi
      WHERE pi.post_id = pm.id AND pi.is_active = true AND pi.assignment_type = 'REGULAR' AND pi.deleted_at IS NULL
  );

-- uq_idx_single_regular_active_occupant / uq_idx_single_regular_active_employee:
-- a post can have at most one active REGULAR incumbent, and a REGULAR
-- employee can hold at most one active REGULAR post at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_idx_single_regular_active_occupant
    ON post_incumbency (post_id) WHERE is_active = true AND assignment_type = 'REGULAR' AND deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_idx_single_regular_active_employee
    ON post_incumbency (employee_id) WHERE is_active = true AND assignment_type = 'REGULAR' AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_incumbency_emp ON post_incumbency (employee_id, is_active);
CREATE INDEX IF NOT EXISTS idx_incumbency_post ON post_incumbency (post_id, is_active);

-- trg_sync_post_vacancy: toggles vacancy_status between OCCUPIED/VACANT as
-- REGULAR incumbency rows are created/ended, and blocks any new incumbency
-- (of any assignment_type) on a FROZEN/ABOLISHED/unbudgeted post outright.
-- Complements (doesn't replace) PostIncumbencyService's own "auto-close the
-- prior Substantive incumbent" application logic - this trigger only
-- concerns itself with vacancy_status/budget, not which incumbency rows
-- exist.
CREATE OR REPLACE FUNCTION fn_sync_post_vacancy_and_budget()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_current_post_status VARCHAR(20);
    v_is_budgeted BOOLEAN;
    v_active_regular_count INT;
BEGIN
    SELECT vacancy_status, is_budgeted
    INTO v_current_post_status, v_is_budgeted
    FROM post_master
    WHERE id = NEW.post_id;

    IF v_current_post_status IN ('ABOLISHED', 'FROZEN') OR v_is_budgeted = false THEN
        RAISE EXCEPTION 'Action Blocked: Post % is currently % (Budgeted: %). No new incumbency permitted.',
            NEW.post_id, v_current_post_status, v_is_budgeted;
    END IF;

    IF (TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.is_active = true)) THEN
        IF NEW.assignment_type = 'REGULAR' AND NEW.is_active = true THEN
            UPDATE post_master SET vacancy_status = 'OCCUPIED', updated_at = now() WHERE id = NEW.post_id;
        END IF;
    END IF;

    IF (TG_OP = 'UPDATE' AND (NEW.is_active = false OR NEW.end_date IS NOT NULL)) THEN
        SELECT COUNT(*) INTO v_active_regular_count
        FROM post_incumbency
        WHERE post_id = NEW.post_id AND is_active = true AND assignment_type = 'REGULAR' AND id <> NEW.id AND deleted_at IS NULL;

        IF v_active_regular_count = 0 THEN
            UPDATE post_master SET vacancy_status = 'VACANT', updated_at = now() WHERE id = NEW.post_id AND vacancy_status = 'OCCUPIED';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_post_vacancy ON post_incumbency;
CREATE TRIGGER trg_sync_post_vacancy
    AFTER INSERT OR UPDATE ON post_incumbency
    FOR EACH ROW EXECUTE FUNCTION fn_sync_post_vacancy_and_budget();
