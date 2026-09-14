package in.gov.jci.hrms.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Self-access check for JCIECCS member-facing reads (financial-position) - mirrors LoanSecurity.isSelf. */
@Component("jciEccsSec")
public class JciEccsSecurity {

    public boolean isSelf(Authentication authentication, Long employeeId) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return callerEmployeeId != null && callerEmployeeId.equals(employeeId);
    }
}
