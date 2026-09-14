package in.gov.jci.hrms.entity;

/** jcieccs_recovery.source - DB-enforced via chk_jcieccs_recovery_source. Deliberately just these three
 * (Phase 1 scope) - SETTLEMENT/MIGRATION/ADJUSTMENT sources are not introduced until those lifecycle
 * paths are actually built. */
public enum JciEccsRecoverySource {
    PAYROLL,
    CASH,
    REVERSAL
}
