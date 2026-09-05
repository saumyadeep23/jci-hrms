package in.gov.jci.hrms.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Path-variable employeeId IS the identity being checked, so no repository lookup is needed. */
@Component("employeeSecurity")
public class EmployeeSecurity {

    public boolean isSelf(Authentication authentication, Long employeeId) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return callerEmployeeId != null && callerEmployeeId.equals(employeeId);
    }
}
