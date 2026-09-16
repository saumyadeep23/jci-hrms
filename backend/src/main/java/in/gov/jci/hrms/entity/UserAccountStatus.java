package in.gov.jci.hrms.entity;

/** application_users.status lifecycle (RBAC_SECURITY_REQUIREMENTS.md / ONBOARDING_SECURITY_REQUIREMENTS.md). */
public enum UserAccountStatus {
    PENDING_INVITATION,
    INVITED,
    ACTIVE,
    LOCKED,
    DISABLED,
    EXPIRED,
    SEPARATED
}
