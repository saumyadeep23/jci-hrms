package in.gov.jci.hrms.entity;

/** cpf_trust_member_ledger_entries.entry_type - what kind of transaction moved a member's CPF Trust balance. */
public enum CpfLedgerEntryType {
    OPENING_BALANCE,
    PAYROLL_MONTHLY,
    DA_ARREAR,
    TRANSFER_IN,
    LOAN_WITHDRAWAL,
    LOAN_REPAYMENT,
    ANNUAL_INTEREST,
    /** Mid-year interest crystallization on exit (superannuation/resignation/death/transfer-out) - see CpfInterestComputationService.crystallizeInterimInterest(). */
    INTERIM_SETTLEMENT_INTEREST,
    FINAL_SETTLEMENT
}
