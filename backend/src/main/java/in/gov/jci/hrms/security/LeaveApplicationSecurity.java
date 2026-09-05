package in.gov.jci.hrms.security;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component("leaveSec")
public class LeaveApplicationSecurity {

    private final LeaveApplicationRepository leaveApplicationRepository;

    public LeaveApplicationSecurity(LeaveApplicationRepository leaveApplicationRepository) {
        this.leaveApplicationRepository = leaveApplicationRepository;
    }

    public boolean isSelf(Authentication authentication, Long leaveApplicationId) {
        return matches(authentication, leaveApplicationId, LeaveApplication::getEmployee);
    }

    /** approverEmployee is the supervisor SupervisorResolutionService resolved when the application was submitted. */
    public boolean isApprover(Authentication authentication, Long leaveApplicationId) {
        return matches(authentication, leaveApplicationId, LeaveApplication::getApproverEmployee);
    }

    private boolean matches(Authentication authentication, Long leaveApplicationId,
                             Function<LeaveApplication, Employee> role) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        if (callerEmployeeId == null) {
            return false;
        }
        return leaveApplicationRepository.findById(leaveApplicationId)
                .map(role)
                .map(Employee::getId)
                .map(callerEmployeeId::equals)
                .orElse(false);
    }
}
