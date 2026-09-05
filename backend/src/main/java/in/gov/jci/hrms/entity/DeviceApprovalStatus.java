package in.gov.jci.hrms.entity;

/** ALMS operational gap #3 - device registration lifecycle. A revoked device stays revoked (no path back to PENDING_APPROVAL/APPROVED) - re-registering the same deviceIdentifier is a new POST, not a status transition. */
public enum DeviceApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REVOKED
}
