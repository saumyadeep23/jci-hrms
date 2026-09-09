package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollMovementInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollMovementInputRepository extends JpaRepository<PayrollMovementInput, Long> {

    List<PayrollMovementInput> findByPayYearAndPayMonthOrderByCreatedAtAsc(int payYear, int payMonth);

    List<PayrollMovementInput> findByMovementIdOrderByPayYearAscPayMonthAsc(Long movementId);

    Optional<PayrollMovementInput> findByMovementIdAndPayMonthAndPayYear(Long movementId, int payMonth, int payYear);

    /** PayrollBatchComputationService's per-employee lookup for a movement month - excludes rows a prior FINALIZED batch already consumed (isPayrollApplied) so a re-run of a still-DRAFT batch keeps finding its own not-yet-locked row. */
    List<PayrollMovementInput> findByEmployee_IdAndPayMonthAndPayYearAndPayrollAppliedFalse(Long employeeId, int payMonth, int payYear);
}
