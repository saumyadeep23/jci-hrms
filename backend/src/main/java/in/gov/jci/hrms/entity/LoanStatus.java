package in.gov.jci.hrms.entity;

public enum LoanStatus {
    SANCTIONED,
    DISBURSED,
    ACTIVE,
    CLOSED,
    FORECLOSED,
    /** Administratively written off - distinct from FORECLOSED (paid to zero) or CLOSED (completed term). Legacy migration only. */
    WRITTEN_OFF
}
