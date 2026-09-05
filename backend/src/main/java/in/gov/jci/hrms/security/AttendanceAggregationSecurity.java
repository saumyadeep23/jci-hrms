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
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_HR_ADMIN") || authority.equals("ROLE_SUPER_ADMIN"));
    }
}
