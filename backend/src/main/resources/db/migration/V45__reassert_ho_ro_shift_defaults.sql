-- Defensive follow-up to V44: on this shared dev database, the 'HO-RO' row
-- (renamed from V43's 'GEN') ended up with grace_period_minutes=30 and the
-- new shift_code applied, but shift_name/full_day_minutes/half_day_minutes/
-- applicable_office_type stayed at their pre-V44 values (shift_name
-- 'General Shift', the other three NULL) - inconsistent with V44's own
-- single-statement UPDATE, which sets all seven columns together and can
-- only apply atomically. Cause not established; re-asserted here
-- idempotently (safe to run whether V44 fully, partially, or not at all
-- applied on a given environment) rather than left inconsistent.
UPDATE shift_master
SET shift_name = 'HO/RO Shift',
    start_time = '09:45',
    end_time = '18:15',
    grace_period_minutes = 30,
    full_day_minutes = 510,
    half_day_minutes = 255,
    applicable_office_type = 'HEAD_OFFICE'
WHERE shift_code = 'HO-RO';
