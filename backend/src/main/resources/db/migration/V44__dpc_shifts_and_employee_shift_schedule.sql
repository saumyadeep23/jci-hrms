-- DPC 6-day work-week shifts, plus columns ShiftResolutionService needs to
-- derive full/half-day credit and (for the admin UI) which office type a
-- shift is meant for.
ALTER TABLE shift_master ADD COLUMN full_day_minutes INTEGER;
ALTER TABLE shift_master ADD COLUMN half_day_minutes INTEGER;
-- 'HEAD_OFFICE' / 'REGIONAL_OFFICE' / 'DPC', or NULL for a shift not tied to
-- a single office type (e.g. the watchmen SHIFT_A/B/C rotations, which an
-- explicit roster assignment - not this default resolution - puts someone
-- on regardless of their posted office).
ALTER TABLE shift_master ADD COLUMN applicable_office_type VARCHAR(20);

-- The pre-existing 'GEN' row (seeded V43) was this codebase's placeholder
-- guess at the HO/RO general shift, with a grace period that didn't
-- actually match JCI Circular JCI/HO/Pers/2024-25/53 (the circular this
-- schema's own hardcoded attendance-evaluation constants were built
-- against - AttendanceAggregationService's STANDARD_START/GRACE_CUTOFF/
-- FULL_DAY_MINUTES/HALF_DAY_MINUTES: 09:45 start, 30 min grace, 510/255
-- minute full/half day). Corrected in place (same row/id) rather than
-- inserted fresh, and renamed to 'HO-RO' to match this ticket's and the
-- resolution service's real lookup code.
UPDATE shift_master
SET shift_code = 'HO-RO',
    shift_name = 'HO/RO Shift',
    start_time = '09:45',
    end_time = '18:15',
    grace_period_minutes = 30,
    full_day_minutes = 510,
    half_day_minutes = 255,
    applicable_office_type = 'HEAD_OFFICE'
WHERE shift_code = 'GEN';

-- DPC's 6-day week: Monday-Friday on DPC_WD, Saturday on the shorter
-- DPC_SAT, Sunday is weekly off (see ShiftResolutionService).
INSERT INTO shift_master (shift_code, shift_name, start_time, end_time, grace_period_minutes, full_day_minutes, half_day_minutes, crosses_midnight, applicable_office_type) VALUES
    ('DPC_WD',  'DPC Weekday Shift',  '10:00', '17:30', 15, 450, 225, false, 'DPC'),
    ('DPC_SAT', 'DPC Saturday Shift', '10:00', '14:30', 15, 270, 135, false, 'DPC');

-- Explicit per-employee/day roster override - ShiftResolutionService checks
-- this before falling back to the office-based default. No admin UI writes
-- to it yet (see DutyRosterPage.tsx's own "roster assignment is a future
-- phase" note) - shift_id NULL on a row that exists means an explicit
-- WEEKLY_OFF override (e.g. a compensatory off), distinct from "no row at
-- all" (fall through to the default resolution).
CREATE TABLE employee_shift_schedule (
    id             BIGSERIAL PRIMARY KEY,
    employee_id    BIGINT NOT NULL REFERENCES employees (id),
    schedule_date  DATE   NOT NULL,
    shift_id       BIGINT REFERENCES shift_master (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_employee_shift_schedule_employee_date ON employee_shift_schedule (employee_id, schedule_date);
