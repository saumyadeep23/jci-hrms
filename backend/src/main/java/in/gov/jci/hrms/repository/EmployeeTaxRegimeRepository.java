package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeTaxRegime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeTaxRegimeRepository extends JpaRepository<EmployeeTaxRegime, Long> {

    Optional<EmployeeTaxRegime> findByEmployee_IdAndFinancialYear(Long employeeId, String financialYear);
}
