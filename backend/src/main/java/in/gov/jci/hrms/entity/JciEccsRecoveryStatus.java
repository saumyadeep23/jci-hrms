package in.gov.jci.hrms.entity;

/** jcieccs_recovery.status - DB-enforced via chk_jcieccs_recovery_status. RECOVERED != POSTED: POSTED
 * means allocations for this recovery have actually been written to the ledger/schedule/outstanding
 * balance (see JciEccsRecoveryPostingService); PARTIAL/FAILED never reach POSTED. */
public enum JciEccsRecoveryStatus {
    PENDING,
    CONFIRMED,
    PARTIAL,
    FAILED,
    POSTED,
    REVERSED,
    RECONCILIATION_REQUIRED
}
