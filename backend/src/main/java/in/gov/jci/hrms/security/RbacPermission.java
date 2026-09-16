package in.gov.jci.hrms.security;

/** Permission codes referenced directly from Java code (subset of the full catalogue seeded by V94). */
public final class RbacPermission {

    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_PROVISION = "USER_PROVISION";
    public static final String USER_INVITE = "USER_INVITE";
    public static final String USER_INVITE_RESEND = "USER_INVITE_RESEND";
    public static final String USER_INVITE_REVOKE = "USER_INVITE_REVOKE";
    public static final String USER_ROLE_ASSIGN = "USER_ROLE_ASSIGN";
    public static final String CPF_SANCTION = "CPF_SANCTION";
    public static final String CPF_DISBURSE = "CPF_DISBURSE";

    private RbacPermission() {
    }
}
