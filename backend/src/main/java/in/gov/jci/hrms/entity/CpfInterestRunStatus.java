package in.gov.jci.hrms.entity;

/**
 * Lifecycle of one cpf_annual_interest_runs row - see CpfInterestRunService's own javadoc for the full
 * Calculate -> Approve/Post -> Reverse -> Recalculate workflow this drives.
 */
public enum CpfInterestRunStatus {
    /** Calculation preview persisted (member breakdown recomputed on demand) - ledger not yet touched. */
    CALCULATED,
    /** ANNUAL_INTEREST ledger entries inserted and subsequent running balances cascaded. */
    POSTED,
    /** ANNUAL_INTEREST_REVERSAL entries inserted for every member this run posted; original entries preserved. */
    REVERSED,
    /** Calculation or posting failed validation - no ledger entries were written. */
    FAILED
}
