package in.gov.jci.hrms.entity;

/**
 * cpf_loan_batch_recovery.outcome (V84) - what actually happened when
 * {@code CpfLedgerSyncService.applyTwoPhaseLoanRecovery} resolved a payroll batch's Head 30/31 amount
 * against a loan's live state at posting time (Part 17: the live-balance recheck immediately before
 * posting). RECOVERED is the normal case; the others exist so an already-payroll-deducted amount that
 * could not be applied (e.g. the loan closed via cash settlement between payroll computation and
 * disbursement) is never silently dropped - it is recorded here for CPF Trust staff to trace/refund
 * (Part 26/43), even though this table does not itself reissue the money.
 */
public enum CpfLoanBatchRecoveryOutcome {
    RECOVERED,
    EXCESS_LOAN_ALREADY_CLOSED,
    EXCESS_NO_OUTSTANDING
}
