package in.gov.jci.hrms.entity;

/** jcieccs_reconciliation.reason_code - DB-enforced via chk_jcieccs_recon_reason. */
public enum JciEccsReconciliationReasonCode {
    PARTIAL_PAYROLL_DEBIT,
    FAILED_PAYROLL_DEBIT,
    OVER_DEBIT,
    POSTING_MISMATCH,
    DUPLICATE_CONFIRMATION,
    CASH_RECOVERY_AFTER_SNAPSHOT_LOCK,
    REVERSAL_AFTER_PAYROLL_POSTING,
    LOAN_BALANCE_MISMATCH,
    SCHEDULE_RECOVERY_MISMATCH,
    UNKNOWN
}
