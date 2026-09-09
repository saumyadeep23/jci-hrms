package in.gov.jci.hrms.entity;

/** payroll_batches.status - matches its DB CHECK constraint exactly. */
public enum PayrollBatchStatus {
    DRAFT,
    HR_FINALIZED,
    FINANCE_APPROVED,
    REJECTED_TO_HR,
    DISBURSED,
    CANCELLED
}
