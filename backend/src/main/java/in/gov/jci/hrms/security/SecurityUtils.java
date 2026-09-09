package in.gov.jci.hrms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Bridges a caller's JWT to the Employee record it represents. There is no
 * separate "user" table in this schema - the JWT's own "employee_id" claim
 * (set by the IdP at token-issuance time, out of this app's control) is the
 * only link between an authenticated principal and an Employee row.
 */
public final class SecurityUtils {

    public static final String EMPLOYEE_ID_CLAIM = "employee_id";

    private SecurityUtils() {
    }

    public static Long currentEmployeeId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }
        Object claim = jwtAuthentication.getToken().getClaim(EMPLOYEE_ID_CLAIM);
        if (claim == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(claim));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The authenticated principal's name (JWT subject) - used to attribute actions like an RO/DPC decommission, not to resolve an Employee row. */
    public static String currentUsername(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

}
