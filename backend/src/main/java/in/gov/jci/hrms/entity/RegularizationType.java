package in.gov.jci.hrms.entity;

/** employee_suspension_records.regularization_type - free VARCHAR(30), no DB CHECK constraint. Set only when status flips to REVOKED. */
public enum RegularizationType {
    REINSTATED,
    DISMISSED,
    COMPULSORILY_RETIRED
}
