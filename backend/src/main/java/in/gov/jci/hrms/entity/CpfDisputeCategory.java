package in.gov.jci.hrms.entity;

/** cpf_transaction_disputes.dispute_category - a small, closed business vocabulary, modeled as a Java enum + DB CHECK constraint (V83), matching this codebase's own established convention for concepts like this (CpfLedgerEntryType, CpfLoanApplicationStatus, ...) rather than a bespoke master-data table. */
public enum CpfDisputeCategory {
    EMPLOYEE_CONTRIBUTION,
    EMPLOYER_CONTRIBUTION,
    EPS_CONTRIBUTION,
    VPF_CONTRIBUTION,
    INTEREST,
    LOAN_SANCTION,
    LOAN_REPAYMENT,
    WITHDRAWAL,
    TRANSACTION_MISSING,
    BALANCE,
    TRANSACTION_DATE,
    OTHER
}
