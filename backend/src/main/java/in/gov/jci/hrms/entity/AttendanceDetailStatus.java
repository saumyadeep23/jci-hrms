package in.gov.jci.hrms.entity;

/**
 * Fine-grained pipeline status computed by AttendanceAggregationService,
 * stored alongside (not replacing) the coarse AttendanceStatus that payroll
 * already reads - see DailyAttendance.toCoarseStatus() for the deterministic
 * mapping down to the frozen 6-value enum.
 */
public enum AttendanceDetailStatus {
    /** Today only: an IN punch has been recorded but the day hasn't been closed out with an OUT punch yet - superseded by a final status (PRESENT/GRACE_APPLIED/etc.) once OUT is punched. */
    IN_PROGRESS,
    PRESENT,
    GRACE_APPLIED,
    LATE_SHORT_HOURS,
    REQUIRES_REGULARIZATION,
    UNAUTHORIZED_LATE,
    HALF_DAY_PRESENT,
    HALF_DAY_SHORT,
    HALF_DAY_ABSENT,
    ON_LEAVE,
    HOLIDAY,
    WEEKOFF,
    ABSENT,
    /** Covers the spec's "ON_TOUR"/"OFFICIAL_DUTY" naming - one status for a day covered by an approved TourRequest. */
    ON_TOUR
}
