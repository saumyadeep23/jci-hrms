package in.gov.jci.hrms.entity;

/** jcieccs_collection_detail.debit_status - DB-enforced via chk_jcieccs_detail_status.
 * RECONCILIATION_REQUIRED (Phase 1): a cash repayment was posted against this detail's loan after this
 * batch was already LOCKED - the locked amount is never silently changed, this flags it instead (see
 * JciEccsLoanService.postCashRepayment). */
public enum JciEccsDebitStatus {
    PENDING_DEBIT,
    DEBIT_SUCCESS,
    DEBIT_PARTIAL,
    DEBIT_FAILED,
    REVERSED,
    RECONCILIATION_REQUIRED
}
