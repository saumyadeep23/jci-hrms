package in.gov.jci.hrms.entity;

/** jcieccs_loan.status - DB-enforced via chk_jcieccs_loan_status. PENDING/DEFAULTED/WRITTEN_OFF are
 * allowed by the DB but not reachable through this module's own lifecycle today (no separate
 * apply-then-sanction step exists - POST /api/jcieccs/loans records an already-disbursed loan, so a new
 * loan starts at ACTIVE). */
public enum JciEccsLoanStatus {
    PENDING,
    ACTIVE,
    RESTRUCTURED,
    CLOSED,
    DEFAULTED,
    WRITTEN_OFF
}
