package in.gov.jci.hrms.entity;

/**
 * The multi-tier routing stage of a LeaveApplication - tracked separately from its own
 * LeaveApplicationStatus, which stays the authoritative state for balance-reservation logic. A
 * DRAFT application has no meaningful workflow stage yet (defaults to SUBMITTED regardless, only
 * becoming accurate once submit() actually runs).
 */
public enum LeaveWorkflowStage {
    SUBMITTED,
    RECOMMENDED,
    SANCTIONED,
    REJECTED,
    CANCELLED
}
