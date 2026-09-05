package in.gov.jci.hrms.entity;

/** DB values ("A+", "A-", ...) aren't valid Java identifiers - see BloodGroupConverter. */
public enum BloodGroup {
    A_POSITIVE,
    A_NEGATIVE,
    B_POSITIVE,
    B_NEGATIVE,
    AB_POSITIVE,
    AB_NEGATIVE,
    O_POSITIVE,
    O_NEGATIVE
}
