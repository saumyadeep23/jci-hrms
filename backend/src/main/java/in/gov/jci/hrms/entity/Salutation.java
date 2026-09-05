package in.gov.jci.hrms.entity;

/** DB values ("Mr.", "Ms.", ...) aren't valid Java identifiers - see SalutationConverter. */
public enum Salutation {
    MR,
    MS,
    MRS,
    DR
}
