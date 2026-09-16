package in.gov.jci.hrms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/** Anyone can trigger evaluation of their own attendance; only HR_ADMIN/SUPER_ADMIN can target someone else's. */
@Component("attendanceAggSec")
public class AttendanceAggregationSecurity {

    public boolean canEvaluateFor(Authentication authentication, Long employeeId) {
        if (employeeId == null) {
            return true;
        }
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId.equals(callerEmployeeId)) {
            return true;
        }
        return canActOnBehalfOfOthers(authentication);
    }

    /**
     * The role-only half of {@link #canEvaluateFor}, extracted so callers
     * that already know they're in the "not the caller's own employeeId"
     * case (e.g. MobilePunchController's SEC-002 defense-in-depth check,
     * docs/security/SEC_001_002_REMEDIATION.md) can reuse the same
     * HR_ADMIN/SUPER_ADMIN rule without re-deriving it.
     */
    public boolean canActOnBehalfOfOthers(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_HR_ADMIN") || authority.equals("ROLE_SUPER_ADMIN"));
    }
}
