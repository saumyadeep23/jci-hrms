package in.gov.jci.hrms.entity;

/** employee_cea_claims.claim_type - no DB CHECK constraint restricts this column (free VARCHAR(30)), so this enum is the only thing constraining it on the write path this codebase controls. */
public enum CeaClaimType {
    CEA,
    HOSTEL_SUBSIDY
}
