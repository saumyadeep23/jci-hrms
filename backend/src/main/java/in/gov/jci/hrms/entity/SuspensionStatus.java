package in.gov.jci.hrms.entity;

/** employee_suspension_records.status - free VARCHAR(30), no DB CHECK constraint, so this enum is the only thing constraining it on the write path. */
public enum SuspensionStatus {
    UNDER_SUSPENSION,
    REVOKED
}
