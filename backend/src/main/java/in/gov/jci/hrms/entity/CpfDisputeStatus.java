package in.gov.jci.hrms.entity;

/**
 * cpf_transaction_disputes.status - the dispute lifecycle (Part 11/30 of the module spec). Allowed
 * transitions are enforced in {@code CpfTransactionDisputeService}, not here:
 * <pre>
 *   OPEN -> UNDER_REVIEW -> RESOLVED
 *   OPEN -> UNDER_REVIEW -> REJECTED
 *   OPEN -> UNDER_REVIEW -> CLARIFICATION_REQUIRED -> UNDER_REVIEW -> (RESOLVED|REJECTED)
 *   OPEN -> WITHDRAWN
 * </pre>
 * RESOLVED/REJECTED are terminal for the ordinary workflow - no employee action and no reviewer action
 * defined here ever transitions out of them (Part 30: "RESOLVED -&gt; OPEN... unless an explicit
 * administrative reopen mechanism already exists" - none does, so none is added).
 */
public enum CpfDisputeStatus {
    OPEN,
    UNDER_REVIEW,
    CLARIFICATION_REQUIRED,
    RESOLVED,
    REJECTED,
    WITHDRAWN
}
