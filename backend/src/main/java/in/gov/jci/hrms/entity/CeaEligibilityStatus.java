package in.gov.jci.hrms.entity;

/**
 * Children Education Allowance eligibility badge for one Family Register row - computed server-side
 * (see DependentResponse.from()), never persisted. Only ever set for relationship SON/DAUGHTER; every
 * other relationship (FATHER/MOTHER/SPOUSE) has no CEA badge at all (null), since CEA is specifically
 * a children's allowance. Divyang eligibility does not additionally require isDependent - a Divyang
 * child up to age 22 qualifies regardless of the ordinary dependent flag; the standard age-20 cutoff
 * does require it. Both age cutoffs are inclusive.
 */
public enum CeaEligibilityStatus {
    ELIGIBLE_STANDARD,
    ELIGIBLE_DIVYANG,
    INELIGIBLE_OVERAGE
}
