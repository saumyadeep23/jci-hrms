-- Catches this migration history up to schema drift already live on the dev DB:
-- regular_pay_fixations.increment_cycle's CHECK constraint had already been widened
-- from JULY/JANUARY-only (V50's original) to all twelve calendar months directly
-- against the dev database, outside Flyway, with no corresponding migration ever
-- committed - the same class of undocumented drift this project has hit before
-- (see V60's header). The Java IncrementCycle enum was never updated to match,
-- so any row using a month other than JULY/JANUARY (161 of ~201 rows in this dev
-- dataset) crashed with "No enum constant IncrementCycle.<MONTH>" the moment any
-- code path fully hydrated that RegularPayFixation entity - payroll compute, LPC
-- certificate generation, movement orders, increment processing, terminal
-- settlement, and the EL encashment admin review queue all do.
--
-- Rationale for twelve months, not reverting to two: increment_cycle tracks each
-- employee's own date-of-joining anniversary month (their real annual increment
-- date), not a fixed IDA/CDA-wide July/January cycle - the live data already
-- reflects that, this migration just makes the constraint (and, in the same
-- commit, the Java enum) agree with it.
ALTER TABLE regular_pay_fixations DROP CONSTRAINT IF EXISTS ck_regular_pay_fixations_increment_cycle;

ALTER TABLE regular_pay_fixations ADD CONSTRAINT ck_regular_pay_fixations_increment_cycle
    CHECK (increment_cycle IN ('JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE',
                                'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'));
