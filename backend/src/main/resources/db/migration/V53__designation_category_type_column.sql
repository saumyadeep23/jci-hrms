-- fn_calculate_jci_superannuation_date (V31) has always read designations.category_type to decide
-- whether an employee's currently-held post is a Director role (60-year/5-year-tenure rule vs. the
-- 58-year regular rule), but no migration ever actually created that column - it only existed on
-- databases that happened to acquire it out-of-band. On a genuinely fresh database (a clean clone,
-- CI, or a new environment) the trigger would fail with "column des.category_type does not exist"
-- on the very first employee insert. This backfills the column so the migration history is
-- self-consistent; IF NOT EXISTS makes it a no-op wherever the column is already present.
ALTER TABLE designations ADD COLUMN IF NOT EXISTS category_type VARCHAR(50);
