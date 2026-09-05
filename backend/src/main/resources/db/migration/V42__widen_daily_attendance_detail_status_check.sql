-- ck_daily_attendance_detail_status (V14) never accounted for two
-- AttendanceDetailStatus Java enum values added later: IN_PROGRESS (the
-- status for "punched in today, not yet punched out" -
-- AttendanceAggregationService.evaluateDay()'s own javadoc documents this as
-- expected, successful behavior) and ON_TOUR. Every first punch-IN of the
-- day computed IN_PROGRESS and was rejected by this CHECK constraint,
-- which - via MobilePunchService.syncDailyAttendance() sharing the punch's
-- own transaction - surfaced as a 500 (UnexpectedRollbackException) on
-- POST /api/attendance/punch itself, not just a logged warning as intended.
ALTER TABLE daily_attendance DROP CONSTRAINT ck_daily_attendance_detail_status;
ALTER TABLE daily_attendance ADD CONSTRAINT ck_daily_attendance_detail_status CHECK (detail_status IS NULL OR detail_status IN
    ('PRESENT', 'GRACE_APPLIED', 'LATE_SHORT_HOURS', 'REQUIRES_REGULARIZATION', 'UNAUTHORIZED_LATE',
     'HALF_DAY_PRESENT', 'HALF_DAY_SHORT', 'HALF_DAY_ABSENT', 'ON_LEAVE', 'HOLIDAY', 'WEEKOFF', 'ABSENT',
     'IN_PROGRESS', 'ON_TOUR'));
