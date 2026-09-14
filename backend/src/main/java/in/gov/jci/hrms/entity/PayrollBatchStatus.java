package in.gov.jci.hrms.entity;

/**
 * payroll_batches.status - matches its DB CHECK constraint exactly. CALCULATED (V78) is the real
 * post-processBatch() state - see PayrollBatchComputationService.processBatch()/finalizeBatch() for
 * the DRAFT -> CALCULATED -> HR_FINALIZED -> DISBURSED progression this enables.
 */
public enum PayrollBatchStatus {
    DRAFT,
    CALCULATED,
    HR_FINALIZED,
    FINANCE_APPROVED,
    REJECTED_TO_HR,
    DISBURSED,
    CANCELLED
}
