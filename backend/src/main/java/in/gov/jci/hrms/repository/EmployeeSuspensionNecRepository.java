package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeSuspensionNec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeSuspensionNecRepository extends JpaRepository<EmployeeSuspensionNec, Long> {

    /** PayrollBatchComputationService's monthly NEC gate check for one suspension. */
    Optional<EmployeeSuspensionNec> findBySuspension_IdAndSalMonthAndSalYear(Long suspensionId, int salMonth, int salYear);
}
