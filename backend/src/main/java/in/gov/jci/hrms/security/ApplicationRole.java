package in.gov.jci.hrms.security;

/**
 * The 18 canonical role codes (RBAC_SECURITY_REQUIREMENTS.md), matching the rows seeded by
 * V94__rbac_core_schema.sql exactly. Roles themselves remain DB-backed reference data (the
 * catalogue can grow without a code change) - these constants exist only so Java code that must
 * name a specific role (bootstrap, tests) doesn't repeat string literals.
 */
public final class ApplicationRole {

    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String USER = "USER";
    public static final String HR_ADMIN = "HR_ADMIN";
    public static final String HR_ADMIN_PERS = "HR_ADMIN_PERS";
    public static final String HR_MAKER_PERS = "HR_MAKER_PERS";
    public static final String HR_ADMIN_BILL = "HR_ADMIN_BILL";
    public static final String HR_MAKER_BILL = "HR_MAKER_BILL";
    public static final String HR_ADMIN_EST = "HR_ADMIN_EST";
    public static final String HR_MAKER_EST = "HR_MAKER_EST";
    public static final String FIN_ADMIN = "FIN_ADMIN";
    public static final String FIN_ADMIN_CPF = "FIN_ADMIN_CPF";
    public static final String FIN_MAKER_CPF = "FIN_MAKER_CPF";
    public static final String FIN_ADMIN_DISB = "FIN_ADMIN_DISB";
    public static final String FIN_MAKER_DISB = "FIN_MAKER_DISB";
    public static final String JCIECCS_ADMIN = "JCIECCS_ADMIN";
    public static final String JCIECCS_MAKER = "JCIECCS_MAKER";
    public static final String IT_ADMIN_STORE = "IT_ADMIN_STORE";
    public static final String IT_MAKER_STORE = "IT_MAKER_STORE";

    private ApplicationRole() {
    }
}
