package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeNpsDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeNpsDeclarationRepository extends JpaRepository<EmployeeNpsDeclaration, Long> {

    Optional<EmployeeNpsDeclaration> findByEmployee_IdAndFinancialYear(Long employeeId, String financialYear);

    List<EmployeeNpsDeclaration> findByEmployee_IdOrderByFinancialYearDesc(Long employeeId);

    List<EmployeeNpsDeclaration> findByFinancialYear(String financialYear);
}
