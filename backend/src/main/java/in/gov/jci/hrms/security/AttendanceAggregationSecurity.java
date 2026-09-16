package in.gov.jci.hrms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/** Anyone can trigger evaluation of their own attendance; only HR_ADMIN can target someone else's. */
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
     * HR_ADMIN rule without re-deriving it.
     *
     * Non-financial RBAC migration (docs/security/RBAC_MIGRATION_REPORT.md): SUPER_ADMIN removed from
     * this shared evaluator, which backs SEC-002/005/006/007 across MobilePunchController,
     * AttendanceRegularizationController, LeaveEncashmentController, and LeaveApplicationController -
     * acting on another employee's attendance/leave is a business (HR) action, not a technical-
     * administration one, so the legacy SUPER_ADMIN "act on behalf of others" grant was an implicit
     * business-authority bypass exactly like the ones closed for CPF/JCIECCS/Payroll.
     */
    public boolean canActOnBehalfOfOthers(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_HR_ADMIN"));
    }
}
