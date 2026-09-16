package in.gov.jci.hrms.security.onboarding;

/** ONBOARDING_SECURITY_REQUIREMENTS.md deterministic candidate classification (single + bulk preview). */
public enum CandidateStatus {
    ELIGIBLE,
    OFFICIAL_EMAIL_MISSING,
    INVALID_OFFICIAL_EMAIL,
    INVALID_OFFICIAL_EMAIL_DOMAIN,
    ALREADY_PROVISIONED,
    ALREADY_ACTIVE,
    INVITATION_PENDING,
    EMPLOYEE_INELIGIBLE
}
