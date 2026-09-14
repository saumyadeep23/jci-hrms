package in.gov.jci.hrms.entity;

/**
 * Normalized CPF settlement bucket for a Trust member - never persisted, always derived at read time by
 * CpfSettlementCalculator from the member's employment status, its terminal_settlements row (if any), and
 * today's date. NOT the same enum as TerminalSettlementStatus (DRAFT/AUDITED/APPROVED/DISBURSED), which
 * this collapses into IN_PROCESS/SETTLED plus the two CPF-specific buckets (NOT_APPLICABLE for members who
 * haven't separated, OVERDUE once the configurable settlement-due-date has passed).
 */
public enum CpfSettlementStatus {
    NOT_APPLICABLE,
    PENDING,
    IN_PROCESS,
    OVERDUE,
    SETTLED
}
