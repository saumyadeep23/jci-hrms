package in.gov.jci.hrms.entity;

/**
 * cpf_loan_applications.recovery_phase - which of the two sequential payroll recovery phases a DISBURSED
 * loan is currently in. PRINCIPAL recovers via Head 30/51 and restores the member's own EE/VPF ledger
 * balance; once outstanding_balance reaches zero the loan flips to INTEREST, which recovers via Head 31 -
 * Trust income, not a credit back to the member's own corpus - and flips to CLOSED once outstanding_interest
 * also reaches zero. See CpfLedgerSyncService's two-phase sync and CpfLoanApplicationService.sanctionLoan().
 */
public enum CpfLoanRecoveryPhase {
    PRINCIPAL,
    INTEREST,
    CLOSED
}
