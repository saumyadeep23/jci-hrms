package in.gov.jci.hrms.entity;

/**
 * jcieccs_loan_product.product_code - free-text VARCHAR(20) at the DB level (no CHECK constraint), but
 * exactly two rows are seeded (V87) and every JCIECCS business rule branches on which one it is, so the
 * Java side pins it to this closed enum for compile-time safety.
 */
public enum JciEccsLoanProductCode {
    TERM,
    EMERGENCY
}
