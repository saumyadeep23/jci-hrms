package in.gov.jci.hrms.entity;

/** DB values ("10TH_SECONDARY", "12TH_HIGHER_SECONDARY", ...) start with a digit, so they can't be the enum constant names themselves - see QualificationLevelConverter. */
public enum QualificationLevel {
    TENTH_SECONDARY,
    TWELFTH_HIGHER_SECONDARY,
    DIPLOMA,
    GRADUATION,
    POST_GRADUATION,
    DOCTORATE_PHD,
    PROFESSIONAL_CERTIFICATION,
    OTHER
}
