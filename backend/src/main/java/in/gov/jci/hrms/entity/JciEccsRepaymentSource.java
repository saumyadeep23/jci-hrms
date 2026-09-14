package in.gov.jci.hrms.entity;

/** jcieccs_loan_repayment.source - DB-enforced via chk_jcieccs_rep_source. REVERSAL (Phase 1) marks a
 * compensating ledger row created by JciEccsRecoveryService.reverseRecovery - always paired with
 * reversal_of_id pointing at the original row it restores. */
public enum JciEccsRepaymentSource {
    PAYROLL,
    CASH,
    ADJUSTMENT,
    REVERSAL
}
