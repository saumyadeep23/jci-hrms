package in.gov.jci.hrms.entity;

/** Shared PENDING/APPROVED/REJECTED gate status - reused by leave_encashment_application's HR/Finance gates and attendance_regularization_applications' HoD gate, matching their identical CHECK constraints in V36. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
