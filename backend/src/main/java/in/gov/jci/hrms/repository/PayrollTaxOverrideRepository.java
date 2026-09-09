package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollTaxOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollTaxOverrideRepository extends JpaRepository<PayrollTaxOverride, Long> {

    Optional<PayrollTaxOverride> findByEmployee_IdAndPayrollYearAndPayrollMonth(Long employeeId, int payrollYear, int payrollMonth);
}
