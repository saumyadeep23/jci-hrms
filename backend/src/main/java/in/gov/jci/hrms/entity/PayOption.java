package in.gov.jci.hrms.entity;

/**
 * employee_deputation_records.pay_option - free VARCHAR(40), no DB CHECK constraint. Only
 * PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE is relevant to JCI's own payroll engine (Head 67) - a
 * FOREIGN_POST_PAY_SCALE deputationist draws the borrowing organization's own pay scale entirely,
 * outside this system.
 */
public enum PayOption {
    PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE,
    FOREIGN_POST_PAY_SCALE
}
