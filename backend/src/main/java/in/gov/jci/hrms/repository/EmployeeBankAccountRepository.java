package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeBankAccountRepository extends JpaRepository<EmployeeBankAccount, Long> {

    List<EmployeeBankAccount> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    Optional<EmployeeBankAccount> findByEmployeeIdAndPrimaryDisbursalTrueAndStatus(
            Long employeeId, in.gov.jci.hrms.entity.BankAccountStatus status);
}
