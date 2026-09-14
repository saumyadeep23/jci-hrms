package in.gov.jci.hrms.entity;

/** cpf_application.status - plain varchar column (no DB-level enum/check constraint exists on this table), so this Java enum is the authoritative value set going forward. Mirrors CpfLoanApplicationStatus's own lifecycle for the older cpf_loan_applications flow. */
public enum CpfApplicationStatus {
    APPLIED,
    SANCTIONED,
    DISBURSED,
    REJECTED,
    CLOSED
}
