package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeLoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, Long> {

    Optional<EmployeeLoan> findByLoanAccountNumber(String loanAccountNumber);

    List<EmployeeLoan> findByEmployeeId(Long employeeId);
}
