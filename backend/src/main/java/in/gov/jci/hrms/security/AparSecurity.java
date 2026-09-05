package in.gov.jci.hrms.security;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeApar;
import in.gov.jci.hrms.repository.EmployeeAparRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * reportingOfficer/reviewingOfficer/acceptingAuthority are assigned explicitly
 * on EmployeeApar at initiation (AparService.initiate()), not resolved live -
 * see that entity's javadoc - so ownership here is a direct FK comparison,
 * not a SupervisorResolutionService walk.
 */
@Component("aparSec")
public class AparSecurity {

    private final EmployeeAparRepository employeeAparRepository;

    public AparSecurity(EmployeeAparRepository employeeAparRepository) {
        this.employeeAparRepository = employeeAparRepository;
    }

    public boolean isSelf(Authentication authentication, Long aparId) {
        return matches(authentication, aparId, EmployeeApar::getEmployee);
    }

    public boolean isReportingOfficer(Authentication authentication, Long aparId) {
        return matches(authentication, aparId, EmployeeApar::getReportingOfficer);
    }

    public boolean isReviewingOfficer(Authentication authentication, Long aparId) {
        return matches(authentication, aparId, EmployeeApar::getReviewingOfficer);
    }

    public boolean isAcceptingAuthority(Authentication authentication, Long aparId) {
        return matches(authentication, aparId, EmployeeApar::getAcceptingAuthority);
    }

    /** Anyone with a designated stage in this APAR's workflow may view it. */
    public boolean isParticipant(Authentication authentication, Long aparId) {
        return isSelf(authentication, aparId)
                || isReportingOfficer(authentication, aparId)
                || isReviewingOfficer(authentication, aparId)
                || isAcceptingAuthority(authentication, aparId);
    }

    private boolean matches(Authentication authentication, Long aparId, Function<EmployeeApar, Employee> role) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        if (callerEmployeeId == null) {
            return false;
        }
        return employeeAparRepository.findById(aparId)
                .map(role)
                .map(Employee::getId)
                .map(callerEmployeeId::equals)
                .orElse(false);
    }
}
