package in.gov.jci.hrms.entity;

/** employee_quarter_allotments.status - matches its DB CHECK constraint exactly (enum name == stored value, no converter needed). */
public enum QuarterAllotmentStatus {
    OCCUPIED,
    VACATED,
    SURRENDERED,
    CANCELLED
}
