package in.gov.jci.hrms.entity;

/**
 * cpf_withdrawal_rule_version.status - matches the Postgres enum type {@code cpf_rule_status} exactly
 * (already live on the shared dev database - see CpfHeadMaster's own javadoc). Note this already includes
 * REQUIRES_CONFIRMATION as a first-class status (Part 33 of the spec) rather than a separate boolean flag,
 * and PENDING_VERIFICATION as a distinct step before PENDING_APPROVAL (Part 3's "Administrator creates rule
 * -> Trust Secretariat verification -> Approving authority" three-step workflow).
 */
public enum CpfRuleStatus {
    DRAFT,
    PENDING_VERIFICATION,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUPERSEDED,
    EXPIRED,
    REQUIRES_CONFIRMATION
}
