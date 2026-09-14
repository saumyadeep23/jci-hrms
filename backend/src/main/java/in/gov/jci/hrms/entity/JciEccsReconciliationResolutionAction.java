package in.gov.jci.hrms.entity;

/** jcieccs_reconciliation.resolution_action - DB-enforced via chk_jcieccs_recon_resolution_action. Only
 * actions genuinely backed by an existing workflow: REVERSE_RECOVERY calls the real Phase 1
 * JciEccsRecoveryService.reverseRecovery; REQUEST_PAYROLL_CORRECTION records intent only (there is no
 * Payroll correction API in this codebase to call) - see JciEccsReconciliationService's own javadoc. */
public enum JciEccsReconciliationResolutionAction {
    ACKNOWLEDGE,
    MARK_RESOLVED,
    REQUEST_PAYROLL_CORRECTION,
    REVERSE_RECOVERY,
    NO_ACTION_REQUIRED
}
