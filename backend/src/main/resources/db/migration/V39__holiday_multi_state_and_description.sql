-- State-wise Yearly Holiday & RH Management Publisher.
--
-- 1) The original unique index only covered (holiday_date), which made it
--    impossible for two different states to ever have their own holiday on
--    the same calendar date (a normal occurrence - e.g. two different states'
--    RH choices landing on the same day). Widen it to (holiday_date, state)
--    so per-state rows can coexist; a national/CENTRAL row (state IS NULL)
--    still can't collide with a same-dated state-specific row's absence of a
--    NULL vs non-NULL clash, since Postgres treats every NULL as distinct in
--    a unique index - pre-existing rows with state IS NULL were never
--    deduplicated against each other either, so this isn't a regression.
-- 2) Adds an optional free-text description/reference-notes column, which
--    the admin Holiday & RH Publisher UI needs and nothing previously
--    modeled.
DROP INDEX uq_holidays_date_active;
CREATE UNIQUE INDEX uq_holidays_date_state_active ON holidays (holiday_date, state) WHERE deleted_at IS NULL;

ALTER TABLE holidays ADD COLUMN description TEXT;
