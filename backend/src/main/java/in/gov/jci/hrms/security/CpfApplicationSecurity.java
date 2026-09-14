package in.gov.jci.hrms.security;

import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Self-access check for CPF withdrawal-application self-service (Task 4 Part 20: "Employee: may apply,
 * may not approve own application") - mirrors LoanSecurity/JciEccsSecurity's own isSelf pattern, resolved
 * via employeeCode since that's what CpfApplicationController's eligibility/apply operate on (not a
 * numeric employeeId path variable like the other two). */
@Component("cpfApplicationSec")
public class CpfApplicationSecurity {

    private final EmployeeRepository employeeRepository;

    public CpfApplicationSecurity(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public boolean isSelf(Authentication authentication, String employeeCode) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        if (callerEmployeeId == null || employeeCode == null) {
            return false;
        }
        return employeeRepository.findByEmployeeCode(employeeCode)
                .map(e -> callerEmployeeId.equals(e.getId()))
                .orElse(false);
    }
}
