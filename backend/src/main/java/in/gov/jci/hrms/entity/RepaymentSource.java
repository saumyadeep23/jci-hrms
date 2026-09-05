package in.gov.jci.hrms.entity;

public enum RepaymentSource {
    PAYROLL_DEDUCTION,
    CASH_DEPOSIT,
    FORECLOSURE,
    /** Historical repayment row brought in via legacy migration, not recorded live. */
    LEGACY_IMPORT
}
