package in.gov.jci.hrms.entity;

/** cpf_loan_applications.loan_type - distinct from LoanTypeCode/DiversionType, which belong to the general-purpose employee_loans/PfDiversion machinery. */
public enum CpfLoanType {
    REFUNDABLE_LOAN,
    NON_REFUNDABLE_WITHDRAWAL
}
