-- The `personnel_no` column (V27) was originally a DB-generated internal
-- sequence code (EMP000123 via generate_employee_personnel_no()/
-- trg_set_employee_personnel_no). It has since been repurposed to hold each
-- employee's real, HR-entered Contributory Provident Fund account number
-- (cpf_ac_no) - no longer something the DB should invent on INSERT.
--
-- Written idempotently: the local dev DB already had this rename applied
-- by hand (outside Flyway) with the old generator trigger left dangling
-- (still referencing NEW.personnel_no, which no longer exists - breaking
-- every INSERT). This migration brings that drift back under version
-- control and is safe to run both on a fresh DB (still has personnel_no)
-- and on the already-drifted dev DB (already has cpf_ac_no).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'employees'
          AND column_name = 'personnel_no'
    ) THEN
        ALTER TABLE public.employees RENAME COLUMN personnel_no TO cpf_ac_no;
    END IF;
END $$;

-- Orphaned generator trigger/function - cpf_ac_no is real data entered by
-- HR (or migrated from the legacy PF register), not DB-generated, so this
-- no longer belongs here. Also currently broken on the drifted dev DB
-- (references NEW.personnel_no, a column that no longer exists).
DROP TRIGGER IF EXISTS trg_set_employee_personnel_no ON public.employees;
DROP FUNCTION IF EXISTS generate_employee_personnel_no();
DROP SEQUENCE IF EXISTS seq_employee_personnel_no;

-- Cosmetic: the unique partial index from V27 silently followed the column
-- rename (Postgres tracks the column, not just its name at creation time)
-- but kept its old name - realign it so it doesn't read as stale/confusing.
ALTER INDEX IF EXISTS uq_employees_personnel_no_active RENAME TO uq_employees_cpf_ac_no_active;

-- Matches the live dev DB, which already enforces this (set by hand as
-- part of the same drift). Safe on a fresh DB too: no employees table rows
-- are seeded via Flyway migrations (seed data is loaded separately), so
-- there are never pre-existing NULLs to violate this at migration time.
ALTER TABLE public.employees ALTER COLUMN cpf_ac_no SET NOT NULL;
