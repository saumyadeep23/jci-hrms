package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollMovementInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollMovementInputRepository extends JpaRepository<PayrollMovementInput, Long> {

    List<PayrollMovementInput> findByPayYearAndPayMonthOrderByCreatedAtAsc(int payYear, int payMonth);

    List<PayrollMovementInput> findByMovementIdOrderByPayYearAscPayMonthAsc(Long movementId);

    Optional<PayrollMovementInput> findByMovementIdAndPayMonthAndPayYear(Long movementId, int payMonth, int payYear);
}
