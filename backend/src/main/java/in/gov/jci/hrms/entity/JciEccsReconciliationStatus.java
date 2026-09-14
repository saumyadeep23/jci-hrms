package in.gov.jci.hrms.entity;

/** jcieccs_reconciliation.status - DB-enforced via chk_jcieccs_recon_status. The three-way comparison
 * result (DEMAND vs ACTUAL RECOVERY vs LEDGER POSTING) for one (collection_detail, component) pair. */
public enum JciEccsReconciliationStatus {
    MATCHED,
    PARTIAL,
    NOT_RECOVERED,
    OVER_RECOVERED,
    POSTING_PENDING,
    POSTING_MISMATCH,
    REVERSED,
    LOCKED_SNAPSHOT_CONFLICT,
    ERROR
}
