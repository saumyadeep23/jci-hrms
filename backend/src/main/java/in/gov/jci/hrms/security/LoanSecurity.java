package in.gov.jci.hrms.security;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("loanSec")
public class LoanSecurity {

    private final EmployeeLoanRepository employeeLoanRepository;

    public LoanSecurity(EmployeeLoanRepository employeeLoanRepository) {
        this.employeeLoanRepository = employeeLoanRepository;
    }

    public boolean isSelf(Authentication authentication, Long loanId) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        if (callerEmployeeId == null) {
            return false;
        }
        return employeeLoanRepository.findById(loanId)
                .map(EmployeeLoan::getEmployee)
                .map(Employee::getId)
                .map(callerEmployeeId::equals)
                .orElse(false);
    }
}
